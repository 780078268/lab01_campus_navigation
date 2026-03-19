import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据结构课程设计 - 校园导航系统
 * 开发者: 2024213672 李韶庸
 */
public class CampusNavSystem {

    static class Building { String name, desc; Building(String n, String d){name=n;desc=d;} }
    static class Edge { String target; int weight; Edge(String t, int w){target=t;weight=w;} }
    static class Road { String from, to; int dist; Road(String f, String t, int d){from=f;to=t;dist=d;} }

    static class DijkstraResult {
        Map<String, Integer> dist;
        Map<String, String> prev;
        DijkstraResult(Map<String, Integer> d, Map<String, String> p) { dist=d; prev=p; }
    }

    static class RouteResult {
        List<String> path;
        List<Integer> segmentDists; // path[i] 到 path[i+1] 的距离
        int totalDist;
        String error;
        boolean isTraversal;
        RouteResult(List<String> p, List<Integer> s, int t, boolean tr) { path=p; segmentDists=s; totalDist=t; isTraversal=tr; }
        RouteResult(String e) { error=e; }
    }

    private final Map<String, Building> buildings = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adjList  = new HashMap<>();
    private final List<Road> roads = new ArrayList<>();
    private String dataFilePath;
    private volatile Map<String, Map<String, Integer>> distMatrix = null; // 距离矩阵缓存

    private synchronized void ensureDistMatrix() {
        if (distMatrix != null) return;
        Map<String, Map<String, Integer>> m = new HashMap<>();
        for (String node : buildings.keySet()) m.put(node, dijkstra(node).dist);
        distMatrix = m;
    }

    private void invalidateDistMatrix() { distMatrix = null; }

    // ─── 数据读写 ──────────────────────────────────────────
    public boolean loadMapData(String filePath) {
        this.dataFilePath = filePath;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line, section = "";
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.equals("[Buildings]")) { section = "Buildings"; continue; }
                if (line.equals("[Roads]"))     { section = "Roads";     continue; }
                if ("Buildings".equals(section)) {
                    String[] p = line.split(",", 2);
                    if (p.length >= 2) {
                        buildings.put(p[0].trim(), new Building(p[0].trim(), p[1].trim()));
                        adjList.putIfAbsent(p[0].trim(), new ArrayList<>());
                    }
                } else if ("Roads".equals(section)) {
                    String[] p = line.split(",");
                    if (p.length == 3) {
                        String u = p[0].trim(), v = p[1].trim();
                        int d = Integer.parseInt(p[2].trim());
                        if (buildings.containsKey(u) && buildings.containsKey(v)) {
                            roads.add(new Road(u, v, d));
                            adjList.get(u).add(new Edge(v, d));
                            adjList.get(v).add(new Edge(u, d));
                        }
                    }
                }
            }
            System.out.println("✅ 数据加载成功！地点: " + buildings.size() + " 道路: " + roads.size());
            return true;
        } catch (IOException | NumberFormatException e) {
            System.out.println("❌ 加载异常: " + e.getMessage()); return false;
        }
    }

    public synchronized void saveMapData() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 数据结构课程设计 - 校园导航系统数据文件\n# 开发者: 2024213672 李韶庸\n\n[Buildings]\n");
        for (Building b : buildings.values()) sb.append(b.name).append(",").append(b.desc).append("\n");
        sb.append("\n[Roads]\n");
        for (Road r : roads) sb.append(r.from).append(",").append(r.to).append(",").append(r.dist).append("\n");
        Files.write(new File(dataFilePath).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ─── Dijkstra 核心 ─────────────────────────────────────
    private DijkstraResult dijkstra(String start) {
        Map<String, Integer> dist = new HashMap<>();
        Map<String, String>  prev = new HashMap<>();
        for (String n : buildings.keySet()) dist.put(n, Integer.MAX_VALUE);
        dist.put(start, 0);
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingInt(n -> dist.getOrDefault(n, Integer.MAX_VALUE)));
        pq.add(start);
        while (!pq.isEmpty()) {
            String u = pq.poll();
            int du = dist.getOrDefault(u, Integer.MAX_VALUE);
            if (du == Integer.MAX_VALUE) continue;
            for (Edge e : adjList.getOrDefault(u, Collections.emptyList())) {
                int alt = du + e.weight;
                if (alt < dist.getOrDefault(e.target, Integer.MAX_VALUE)) {
                    dist.put(e.target, alt);
                    prev.put(e.target, u);
                    pq.add(e.target);
                }
            }
        }
        return new DijkstraResult(dist, prev);
    }

    private List<String> buildPath(Map<String, String> prev, String start, String end) {
        List<String> path = new ArrayList<>();
        for (String at = end; at != null; at = prev.get(at)) path.add(at);
        Collections.reverse(path);
        if (path.isEmpty() || !path.get(0).equals(start)) return Collections.emptyList();
        return path;
    }

    private List<Integer> computeSegmentDists(List<String> path) {
        List<Integer> segs = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            String u = path.get(i), v = path.get(i + 1);
            for (Edge e : adjList.getOrDefault(u, Collections.emptyList())) {
                if (e.target.equals(v)) { segs.add(e.weight); break; }
            }
        }
        return segs;
    }

    // ─── 最短路径 ───────────────────────────────────────────
    public RouteResult findShortestPath(String start, String end) {
        if (!buildings.containsKey(start) || !buildings.containsKey(end))
            return new RouteResult("起点或终点名称不存在，请重新搜索");
        DijkstraResult dr = dijkstra(start);
        int total = dr.dist.getOrDefault(end, Integer.MAX_VALUE);
        if (total == Integer.MAX_VALUE) return new RouteResult("路径不可达");
        List<String> path = buildPath(dr.prev, start, end);
        return new RouteResult(path, computeSegmentDists(path), total, false);
    }

    // ─── 遍历所有建筑（最近邻贪心）────────────────────────────
    public RouteResult traverseAll(String start) {
        if (!buildings.containsKey(start)) return new RouteResult("起点不存在");
        ensureDistMatrix(); // 首次调用时预计算，后续直接用缓存
        // 只遍历非路口节点
        List<String> targets = buildings.values().stream()
            .filter(b -> !b.desc.equals("路口"))
            .map(b -> b.name)
            .collect(Collectors.toList());
        Set<String> unvisited = new LinkedHashSet<>(targets);
        unvisited.remove(start);

        List<String> visitOrder = new ArrayList<>();
        List<Integer> segDists  = new ArrayList<>();
        visitOrder.add(start);
        int totalDist = 0;
        String current = start;

        while (!unvisited.isEmpty()) {
            Map<String, Integer> dists = distMatrix.get(current); // O(1) 查表
            String nearest = null; int nearestDist = Integer.MAX_VALUE;
            for (String node : unvisited) {
                int d = dists.getOrDefault(node, Integer.MAX_VALUE);
                if (d < nearestDist) { nearestDist = d; nearest = node; }
            }
            if (nearest == null || nearestDist == Integer.MAX_VALUE) break;
            visitOrder.add(nearest);
            segDists.add(nearestDist);
            totalDist += nearestDist;
            unvisited.remove(nearest);
            current = nearest;
        }
        return new RouteResult(visitOrder, segDists, totalDist, true);
    }

    // ─── 搜索建筑 ───────────────────────────────────────────
    public List<Map<String, String>> searchBuildings(String query) {
        List<Map<String, String>> exact = new ArrayList<>(), prefix = new ArrayList<>(), contains = new ArrayList<>();
        for (Building b : buildings.values()) {
            Map<String, String> item = new HashMap<>();
            item.put("name", b.name); item.put("desc", b.desc);
            if (query == null || query.isEmpty()) { exact.add(item); continue; }
            if (b.name.equals(query)) exact.add(item);
            else if (b.name.startsWith(query)) prefix.add(item);
            else if (b.name.contains(query) || b.desc.contains(query)) contains.add(item);
        }
        List<Map<String, String>> result = new ArrayList<>();
        result.addAll(exact); result.addAll(prefix); result.addAll(contains);
        return result;
    }

    // ─── CRUD 操作 ──────────────────────────────────────────
    public synchronized void addBuilding(String name, String desc) throws Exception {
        if (name == null || name.isEmpty()) throw new Exception("名称不能为空");
        if (name.contains(",")) throw new Exception("名称不能包含逗号");
        if (desc != null && desc.contains(",")) throw new Exception("描述不能包含逗号");
        if (buildings.containsKey(name)) throw new Exception("建筑已存在: " + name);
        buildings.put(name, new Building(name, desc));
        adjList.put(name, new ArrayList<>());
        invalidateDistMatrix();
        saveMapData();
    }

    public synchronized void removeBuilding(String name) throws Exception {
        if (!buildings.containsKey(name)) throw new Exception("建筑不存在: " + name);
        buildings.remove(name);
        adjList.remove(name);
        roads.removeIf(r -> r.from.equals(name) || r.to.equals(name));
        for (List<Edge> edges : adjList.values()) edges.removeIf(e -> e.target.equals(name));
        invalidateDistMatrix();
        saveMapData();
    }

    public synchronized void addRoad(String from, String to, int dist) throws Exception {
        if (!buildings.containsKey(from)) throw new Exception("起点不存在: " + from);
        if (!buildings.containsKey(to))   throw new Exception("终点不存在: " + to);
        for (Road r : roads)
            if ((r.from.equals(from) && r.to.equals(to)) || (r.from.equals(to) && r.to.equals(from)))
                throw new Exception("该道路已存在");
        roads.add(new Road(from, to, dist));
        adjList.get(from).add(new Edge(to, dist));
        adjList.get(to).add(new Edge(from, dist));
        invalidateDistMatrix();
        saveMapData();
    }

    public synchronized void removeRoad(String from, String to) throws Exception {
        boolean ok = roads.removeIf(r ->
            (r.from.equals(from) && r.to.equals(to)) || (r.from.equals(to) && r.to.equals(from)));
        if (!ok) throw new Exception("道路不存在");
        adjList.getOrDefault(from, Collections.emptyList()).removeIf(e -> e.target.equals(to));
        adjList.getOrDefault(to,   Collections.emptyList()).removeIf(e -> e.target.equals(from));
        invalidateDistMatrix();
        saveMapData();
    }

    // ─── HTTP 服务器 ────────────────────────────────────────
    public void startServer(int port, String dataDir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/")) { exchange.sendResponseHeaders(404,-1); return; }
            serveFile(exchange, new File(dataDir, "index.html"), "text/html; charset=UTF-8");
        });
        server.createContext("/annotate", exchange ->
            serveFile(exchange, new File(dataDir, "annotate.html"), "text/html; charset=UTF-8"));
        server.createContext("/map.jpg", exchange ->
            serveFile(exchange, new File(dataDir, "北邮本部地图.jpg"), "image/jpeg"));

        server.createContext("/api/buildings", exchange -> {
            addCorsHeaders(exchange);
            List<Map<String, String>> results = searchBuildings(getQueryParam(exchange, "q"));
            if (results.size() > 12) results = results.subList(0, 12);
            sendJson(exchange, buildingsToJson(results));
        });

        server.createContext("/api/route", exchange -> {
            addCorsHeaders(exchange);
            sendJson(exchange, routeResultToJson(
                findShortestPath(getQueryParam(exchange, "from"), getQueryParam(exchange, "to"))));
        });

        server.createContext("/api/traverse", exchange -> {
            addCorsHeaders(exchange);
            sendJson(exchange, routeResultToJson(traverseAll(getQueryParam(exchange, "start"))));
        });

        server.createContext("/api/coordinates", exchange -> {
            addCorsHeaders(exchange);
            File coordFile = new File(dataDir, "coordinates.json");
            if ("GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, coordFile.exists()
                    ? new String(Files.readAllBytes(coordFile.toPath()), StandardCharsets.UTF_8) : "{}");
            } else if ("POST".equals(exchange.getRequestMethod())) {
                Files.write(coordFile.toPath(), readBody(exchange).getBytes(StandardCharsets.UTF_8));
                sendJson(exchange, "{\"ok\":true}");
            } else { exchange.sendResponseHeaders(405,-1); }
        });

        // 建筑 CRUD
        server.createContext("/api/building", exchange -> {
            addCorsHeaders(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405,-1); return; }
            String body = readBody(exchange);
            String action = extractJsonString(body, "action");
            try {
                if ("add".equals(action)) {
                    addBuilding(extractJsonString(body, "name"), extractJsonString(body, "desc"));
                } else if ("delete".equals(action)) {
                    removeBuilding(extractJsonString(body, "name"));
                } else throw new Exception("未知操作");
                sendJson(exchange, "{\"ok\":true}");
            } catch (Exception e) {
                sendJson(exchange, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // 道路 CRUD
        server.createContext("/api/road", exchange -> {
            addCorsHeaders(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405,-1); return; }
            String body = readBody(exchange);
            String action = extractJsonString(body, "action");
            try {
                if ("add".equals(action)) {
                    Integer dist = extractJsonInt(body, "dist");
                    if (dist == null || dist <= 0) throw new Exception("距离必须为正整数");
                    addRoad(extractJsonString(body, "from"), extractJsonString(body, "to"), dist);
                } else if ("delete".equals(action)) {
                    removeRoad(extractJsonString(body, "from"), extractJsonString(body, "to"));
                } else throw new Exception("未知操作");
                sendJson(exchange, "{\"ok\":true}");
            } catch (Exception e) {
                sendJson(exchange, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("🌐 服务器已启动: http://localhost:" + port);
        System.out.println("🗺️  标注工具:     http://localhost:" + port + "/annotate");
    }

    // ─── 工具方法 ───────────────────────────────────────────
    private void serveFile(HttpExchange exchange, File file, String contentType) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String getQueryParam(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return "";
        for (String param : raw.split("&")) {
            if (param.startsWith(key + "=")) {
                try { return URLDecoder.decode(param.substring(key.length() + 1), StandardCharsets.UTF_8); }
                catch (Exception e) { return ""; }
            }
        }
        return "";
    }

    // ─── JSON 构建 ──────────────────────────────────────────
    private String buildingsToJson(List<Map<String, String>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escapeJson(list.get(i).get("name")))
              .append("\",\"desc\":\"").append(escapeJson(list.get(i).get("desc"))).append("\"}");
        }
        return sb.append("]").toString();
    }

    private String routeResultToJson(RouteResult r) {
        if (r.error != null) return "{\"error\":\"" + escapeJson(r.error) + "\"}";
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalDist\":").append(r.totalDist);
        sb.append(",\"isTraversal\":").append(r.isTraversal);
        sb.append(",\"path\":[");
        for (int i = 0; i < r.path.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(r.path.get(i))).append("\"");
        }
        sb.append("],\"segmentDists\":[");
        if (r.segmentDists != null) {
            for (int i = 0; i < r.segmentDists.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(r.segmentDists.get(i));
            }
        }
        return sb.append("]}").toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"");
    }

    // ─── 简易 JSON 解析 ─────────────────────────────────────
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '"') break;
            if (c == '\\' && idx + 1 < json.length()) {
                char next = json.charAt(++idx);
                sb.append(next == 'n' ? '\n' : next);
            } else sb.append(c);
            idx++;
        }
        return sb.toString();
    }

    private Integer extractJsonInt(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == idx) return null;
        return Integer.parseInt(json.substring(idx, end));
    }

    // ─── 路径自动检测 ───────────────────────────────────────
    private static String resolveDataDir(String[] args) {
        // 1. 命令行参数
        if (args.length > 0 && new File(args[0]).isDirectory()) return args[0];
        // 2. 相对当前工作目录的 java/
        if (new File("java/test.txt").exists()) return "java";
        // 3. 相对 class/JAR 位置向上查找
        try {
            File loc = new File(CampusNavSystem.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            File dir = loc.isFile() ? loc.getParentFile() : loc;
            for (int i = 0; i < 6; i++) {
                File candidate = new File(dir, "java");
                if (new File(candidate, "test.txt").exists())
                    return candidate.getAbsolutePath();
                File parent = dir.getParentFile();
                if (parent == null) break;
                dir = parent;
            }
        } catch (Exception ignored) {}
        return "java"; // 兜底
    }

    // ─── 跨平台打开浏览器 ──────────────────────────────────
    private static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (IOException e) {
            System.out.println("请手动打开浏览器访问: " + url);
        }
    }

    public static void main(String[] args) throws IOException {
        CampusNavSystem system = new CampusNavSystem();
        String dataDir = resolveDataDir(args);
        System.out.println("📂 数据目录: " + new File(dataDir).getAbsolutePath());
        if (!system.loadMapData(dataDir + "/test.txt")) {
            System.out.println("❌ 找不到数据文件，请用 run.sh / run.bat 启动，或在项目根目录执行。");
            return;
        }
        system.startServer(8080, dataDir);
        openBrowser("http://localhost:8080");
        System.out.println("按 Ctrl+C 退出服务器");
    }
}
