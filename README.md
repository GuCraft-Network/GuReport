# GuReport

使用RedisBungee Channel使举报消息在RedisBungee代理间互通，同时拥有快捷操作。

Redis部分和指令处理部分全部由DeepSeek编写或优化，本人无JAVA基础，差不多算屎山。

原理？举报时生成举报ID并传至Redis，并使用RedisBungee Channel发送举报ID给各个代理，各个代理收到举报ID后构建举报消息。

非常垃圾的逻辑，但也只能凑合用咯。

![](https://image.linmoyu.top/20250407223308494.webp)

测试服务器：
1. mc.163.cn - KKCraft

已知BUG，或许很久以后修复/不修复？：

1. RedisBungee没有提供API返回所有代理玩家List，所以TAB补全玩家只返回当前代理的玩家。
2. 由于RedisBungee导致的偶尔找不到在线玩家或需要匹配玩家名称大小写。
3. 构建举报消息时需要先查询“该玩家被举报次数”，然后传入“举报次数”给构建举报消息方法。
