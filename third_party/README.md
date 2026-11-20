# 补充许可材料

`javax.inject-1-sources.jar` 从Maven Central精确版本路径取得，SHA-256为
`C4B87EE2911C139C3DAF498A781967F1EB2E75BC1A8529A2E7B328A15D0E433E`。
`javax/inject/Inject.java` 保留原始版权及Apache-2.0许可头。该文件只用于许可证据，不作为新增编译依赖。

`Apache-2.0.txt` 从本机使用的Gradle9.3.1发行包LICENSE中提取Apache条款至“END OF TERMS AND CONDITIONS”，未改写条款。

`datastore-preferences-external-protobuf-1.1.4-sources.jar` 是Google官方Maven同版本源码包，但内容仅有空目录和MANIFEST，没有实际源码或许可正文。
不能将它当成已取得protobuf源码。该组件的BSD-3-Clause证据目前来自精确POM，进一步的版权/NOTICE逐文件核对仍待完成。

完整解析清单与证据类型见工程根目录THIRD_PARTY_NOTICES.md以及交付evidence中的runtime-licenses.csv。
许可字段可核实不等于已完成整个APK的素材、NOTICE和传递代码审计。

2026-11-12补存173份精确版本POM及父POM于`poms/`，对应169个运行时解析组件及父POM证据。已有缓存中缺失的文件从Google Maven或Maven Central获取，未按依赖家族猜测许可。
`poms/manifest.json`记录每份文件的精确坐标、官方仓库URL、SHA-256及本次取得方式；缓存副本与本次直接下载分别标注。
`scripts/cache_license_poms.py`用于重新收集；`scripts/make_notices.py`优先使用随源码保存的POM，再回退到本机缓存。
