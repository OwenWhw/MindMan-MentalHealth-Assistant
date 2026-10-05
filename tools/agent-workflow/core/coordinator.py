"""Coordinator：按配置装配 Agent 并顺序编排，产出可追溯的执行轨迹。"""

import time

from core.agent import SharedContext


class Coordinator:
    def __init__(self, config, agents, llm=None):
        self.config = config
        self.agents = agents
        self.llm = llm

    def run(self):
        ctx = SharedContext(self.config, logger=print)
        print("=" * 78)
        print("多 Agent 测试工作流启动")
        print("  目标服务 : %s" % self.config.get("baseUrl"))
        print("  驱动模型 : %s (%s)" % (
            self.config.get("llm", {}).get("model"),
            "启用" if self.config.get("llm", {}).get("enabled") else "禁用/降级"))
        print("  Agent 链 : %s" % " → ".join(a.name for a in self.agents))
        print("=" * 78)

        start = time.time()
        for agent in self.agents:
            agent.execute(ctx)

        ctx.elapsed = time.time() - start
        print("-" * 78)
        print("工作流结束，总耗时 %.1fs" % ctx.elapsed)
        return ctx
