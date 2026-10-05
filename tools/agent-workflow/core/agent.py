"""Agent 基类与共享上下文。

每个 Agent 只做一件事，通过 SharedContext 传递产物（用例、结果、归因、报告），
避免 Agent 之间直接耦合——这样新增 Agent（功能测试/代码审查）无需改动已有 Agent。
"""

import time


class SharedContext:
    """流水线共享黑板：各 Agent 的输入输出都挂在这里。"""

    def __init__(self, config, logger=None):
        self.config = config
        self.logger = logger
        self.token = None
        self.cases = []          # spec Agent 产物：测试用例
        self.results = []        # executor Agent 产物：执行结果
        self.analyses = []       # analysis Agent 产物：失败归因
        self.extras = {}         # 其它 Agent 的自由产物（如代码审查结论）
        self.started_at = time.strftime("%Y-%m-%d %H:%M:%S")
        self.trace = []          # 执行轨迹：哪个 Agent 做了什么、耗时

    def log(self, agent, message):
        line = "  [%s] %s" % (agent, message)
        print(line)
        self.trace.append({"agent": agent, "message": message, "t": time.strftime("%H:%M:%S")})


class BaseAgent:
    """Agent 基类：子类实现 run(ctx) -> None（产物写入 ctx）。"""

    name = "base"
    role = "基础 Agent"
    uses_llm = False

    def __init__(self, llm=None, cfg=None):
        self.llm = llm
        self.cfg = cfg or {}

    def run(self, ctx):  # pragma: no cover - 抽象方法
        raise NotImplementedError

    def execute(self, ctx):
        """带计时与异常隔离的执行包装：单个 Agent 失败不中断整条流水线。"""
        ctx.log(self.name, "开始（%s）" % self.role)
        start = time.time()
        try:
            self.run(ctx)
            ok = True
        except Exception as e:
            ctx.log(self.name, "异常：%s" % str(e)[:160])
            ok = False
        cost = time.time() - start
        ctx.log(self.name, "结束，耗时 %.1fs%s" % (cost, "" if ok else "（失败）"))
        return ok
