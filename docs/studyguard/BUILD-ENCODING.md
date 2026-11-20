# Windows中文路径构建修复

验证日期：2026-11-12。适用于本工程使用的JDK21与Gradle9.3.1。

原失败发生在Gradle测试工作进程启动阶段，提示找不到`worker.org.gradle.process.internal.worker.GradleWorkerMain`，并非测试断言失败。
对同一个中文路径下的工作进程JAR执行Java启动器`--dry-run`，UTF-8参数文件不能找到入口类，GBK参数文件成功。2026-11-11以匹配Windows原生编码的设置运行标准测试成功。

工程现采用`org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT`。
JDK21的COMPAT取当前系统的`native.encoding`，避免把GBK固定给所有机器；JavaCompile源码编码及Test进程的`file.encoding`仍显式设为UTF-8。
原始Kotlin源码和Gradle配置文件继续保存为UTF-8，没有把项目批量转码。
JDK行为说明见[Oracle JDK21支持的编码](https://docs.oracle.com/en/java/javase/21/intl/supported-encodings.html)。

验证包含两个独立入口：

1. 完整组合：主APK、设备测试APK、标准JVM测试、依赖清单、Lint，构建成功。
2. 交付的一键脚本：标准测试任务带`--rerun`强制重新执行成功。

两个入口执行的是同一组测试，不能相加；0.3.0-dev共有160项逻辑测试，零失败、零错误、零跳过，不包含Android设备测试。
生成的`standard-tests.json`从标准Gradle测试XML汇总，省略主机名称；保留每组测试的时间及计数。

Windows PowerShell复现：

```powershell
.\scripts\Build-StudyGuard.ps1 -Tasks ':app:testDebugUnitTest','--rerun'
python .\scripts\summarize_tests.py
```

构建脚本保留当前TEMP/TMP，不改写系统设置。使用已有离线依赖时可加`-Offline`。
Android模拟器启动仍受独立的Windows error5影响，编码修复没有使模拟器验收自动通过。
