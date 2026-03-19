# 校园导航系统

基于 Java 实现的课程设计项目，面向北邮本部校园场景，提供建筑检索、最短路径查询、全校园遍历建议、地图节点标注以及基础地图数据管理功能。

项目采用“单文件 Java 后端 + 原生 HTML/CSS/JavaScript 前端”的实现方式，适合课程展示、本地运行和二次修改。

## 项目功能

- 建筑检索：支持按名称精确匹配、前缀匹配和模糊搜索
- 最短路径导航：基于 Dijkstra 算法计算起点到终点的最短路线
- 全建筑遍历：基于最近邻贪心策略生成校园遍历顺序
- 地图可视化：在地图上绘制路径、起终点和已标注节点
- 坐标标注工具：支持为建筑或路口手动标注地图坐标并保存
- 数据管理：支持在页面中新增/删除建筑、新增/删除道路

## 技术实现

### 后端

- 语言：Java
- HTTP 服务：`com.sun.net.httpserver.HttpServer`
- 图存储：邻接表
- 最短路算法：Dijkstra

核心代码位于 [CampusNavSystem.java](/Users/buptniaosuan/Desktop/BUPT.DS/lab01_campus_navigation/java/src/CampusNavSystem.java)。

### 前端

- 导航主页：[index.html](/Users/buptniaosuan/Desktop/BUPT.DS/lab01_campus_navigation/java/index.html)
- 坐标标注页：[annotate.html](/Users/buptniaosuan/Desktop/BUPT.DS/lab01_campus_navigation/java/annotate.html)

前端使用原生 HTML、CSS、JavaScript 编写，通过调用本地 HTTP API 与后端交互。

## 项目结构

```text
lab01_campus_navigation
├── README.md
├── java
│   ├── src
│   │   └── CampusNavSystem.java   # 后端主程序
│   ├── index.html                 # 导航主页
│   ├── annotate.html              # 坐标标注页面
│   ├── test.txt                   # 地图数据文件
│   ├── coordinates.json           # 节点坐标文件
│   └── 北邮本部地图.jpg            # 地图底图
└── out
    └── production
        └── java                   # 已编译 class 文件
```

## 数据说明

### 1. 地图数据文件

项目默认读取 [test.txt](/Users/buptniaosuan/Desktop/BUPT.DS/lab01_campus_navigation/java/test.txt)。

文件分为两个部分：

- `[Buildings]`：记录地点名称和描述
- `[Roads]`：记录两个地点之间的双向道路及距离

示例：

```text
[Buildings]
图书馆南门,图书馆出入口
主楼路口,路口

[Roads]
图书馆南门,图书馆路口,30
图书馆路口,主楼路口,120
```

### 2. 坐标文件

[coordinates.json](/Users/buptniaosuan/Desktop/BUPT.DS/lab01_campus_navigation/java/coordinates.json) 用于保存地图节点坐标，格式如下：

```json
{
  "图书馆南门": [0.5123, 0.4188],
  "主楼路口": [0.4631, 0.3772]
}
```

其中两个数分别表示节点在地图上的相对横纵坐标，取值范围通常在 `0` 到 `1` 之间。

## 运行方式

### 方式一：直接运行已编译版本

如果 `out/production/java` 中已有可用的 `.class` 文件，可以在项目根目录执行：

```bash
cd /Users/buptniaosuan/Desktop/BUPT.DS/lab01_campus_navigation
java -cp out/production/java CampusNavSystem
```

### 方式二：重新编译再运行

在项目根目录执行：

```bash
cd /Users/buptniaosuan/Desktop/BUPT.DS/lab01_campus_navigation
javac -d out/production/java java/src/CampusNavSystem.java
java -cp out/production/java CampusNavSystem
```

程序启动后默认监听：

```text
http://localhost:8080
```

常用页面：

- 导航主页：`http://localhost:8080/`
- 坐标标注：`http://localhost:8080/annotate`

## API 接口

### 建筑查询

```http
GET /api/buildings?q=关键词
```

### 最短路径

```http
GET /api/route?from=起点&to=终点
```

### 遍历所有建筑

```http
GET /api/traverse?start=起点
```

### 获取/保存坐标

```http
GET /api/coordinates
POST /api/coordinates
```

### 建筑管理

```http
POST /api/building
```

请求体示例：

```json
{ "action": "add", "name": "创新楼B座", "desc": "教学楼" }
```

### 道路管理

```http
POST /api/road
```

请求体示例：

```json
{ "action": "add", "from": "图书馆南门", "to": "主楼路口", "dist": 120 }
```

## 算法说明

### 最短路径

系统使用 Dijkstra 算法，以建筑和路口作为图中的节点，以道路距离作为边权，计算两点之间最短路径。

### 全建筑遍历

系统从指定起点出发，每一步选择当前最近的未访问建筑，生成一条近似最优的遍历路径。该方法实现简单、效率较高，但不保证全局最优。

## 使用流程

1. 启动 Java 程序
2. 打开浏览器访问 `http://localhost:8080`
3. 输入起点和终点进行导航
4. 如需补充节点坐标，进入 `/annotate` 页面进行标注
5. 如需修改数据，可在导航页右上角使用“管理”面板

## 注意事项

- 项目默认端口为 `8080`
- 默认数据目录为 `java`
- 程序启动时会读取 `java/test.txt`
- 添加建筑后，建议及时到标注页面补充坐标，否则路线只能文字展示
- `main` 方法中调用了 `open` 命令自动打开浏览器，该行为适合 macOS；如果在 Windows 或 Linux 上运行，可能需要删除或改写这部分逻辑

## 适用场景

- 数据结构课程设计
- 图论与最短路径算法演示
- 校园地图建模练习
- Java Web 本地应用入门实践

## 作者信息

- 项目名称：校园导航系统
- 课程背景：数据结构课程设计
- 开发者：2024213672 李韶庸
