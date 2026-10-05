# MindMan 一键测试：JMeter → Postman → Playwright

从仓库根目录运行 `scripts/run-test-suite.ps1`。脚本依次执行：

1. JMeter 对已登录用户的 `GET /api/emotion/garden` 做小流量查询测试（默认 5 线程、每线程 3 次）；断言 HTTP 200、业务码 200、`data` 为数组。
2. Newman 运行 Postman 集合：登录、获取当前用户、查询花园、验证评分 0 被拒绝，并复查花园记录未增加。集合可直接导入 Postman 查看和编辑。
3. Playwright 执行前端现有的 E2E 测试。

JMeter 和 Postman 使用**真实后端**。现有 Playwright 用例中有模拟 API 的页面流程；它们不替代真实接口测试。

## 首次准备（Windows PowerShell）

- 启动 MindMan 后端，并准备一个专用测试账号。默认地址是 `http://127.0.0.1:8080`。
- 安装 Apache JMeter，设置 `JMETER_HOME`，把 `jmeter` 加入 PATH，或运行时传 `-JMeterPath`。脚本也会查找当前用户 `Downloads/apache-jmeter-*`。
- 在 `tests` 目录执行 `npm install`，安装 Newman；前端 `code/ai-vue` 目录也需要执行过 `npm install` 和 `npx playwright install chromium`。

```powershell
# 先在终端进入本项目根目录
$env:MINDMAN_TEST_USERNAME = '你的测试账号'
$env:MINDMAN_TEST_PASSWORD = '你的测试密码'
.\scripts\run-test-suite.ps1 -CheckOnly
.\scripts\run-test-suite.ps1
```

如果 JMeter 安装在自定义位置：

```powershell
.\scripts\run-test-suite.ps1 -JMeterPath 'C:\path\to\apache-jmeter\bin\jmeter.bat'
```

可以通过 `-Threads 5 -Loops 3 -RampUpSeconds 10` 调整轻量负载。默认只允许本机地址；对自己管理的测试服务器运行时，需要同时传 `-BaseUrl` 与 `-AllowRemote`。

脚本会先做一次登录预检。任一阶段失败就停止，并指出报告目录。报告保存在 `test-results/integration/<运行时间>/`，包括 JMeter JTL/HTML、Postman JUnit XML 与 Playwright JUnit XML。Postman 使用的临时环境文件包含测试账号密码，运行结束后会删除；不要把控制台或报告中的令牌分享给别人。

这是一组学习和回归用的小流量测试；要得出容量结论，还需在独立测试环境设置目标负载、持续时间和服务器监控。
