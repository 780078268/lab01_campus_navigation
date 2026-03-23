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
 * 北邮校园导航系统
 */
public class CampusNavSystem {

    static class Building {
        String name;
        String desc;

        Building(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    static class Edge {
        String target;
        int weight;

        Edge(String target, int weight) {
            this.target = target;
            this.weight = weight;
        }
    }

    static class Road {
        String from;
        String to;
        int dist;

        Road(String from, String to, int dist) {
            this.from = from;
            this.to = to;
            this.dist = dist;
        }
    }

    static class DijkstraResult {
        Map<String, Integer> dist;
        Map<String, String> prev;

        DijkstraResult(Map<String, Integer> dist, Map<String, String> prev) {
            this.dist = dist;
            this.prev = prev;
        }
    }

    static class RouteResult {
        List<String> path;
        List<Integer> segmentDists;
        int totalDist;
        String error;
        boolean isTraversal;
        List<String> matchedStarts;
        List<String> matchedEnds;
        List<RouteOption> routeOptions;

        RouteResult(List<String> path, List<Integer> segmentDists, int totalDist, boolean isTraversal) {
            this.path = path;
            this.segmentDists = segmentDists;
            this.totalDist = totalDist;
            this.isTraversal = isTraversal;
        }

        RouteResult(String error) {
            this.error = error;
        }
    }

    static class RouteOption {
        String from;
        String to;
        int totalDist;

        RouteOption(String from, String to, int totalDist) {
            this.from = from;
            this.to = to;
            this.totalDist = totalDist;
        }
    }

    private final Map<String, Building> buildings = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adjList = new HashMap<>();
    private final List<Road> roads = new ArrayList<>();
    private String dataFilePath;
    private volatile Map<String, Map<String, Integer>> distMatrix;

    private synchronized void ensureDistMatrix() {
        if (distMatrix != null) {
            return;
        }
        Map<String, Map<String, Integer>> matrix = new HashMap<>();
        for (String node : buildings.keySet()) {
            matrix.put(node, dijkstra(node).dist);
        }
        distMatrix = matrix;
    }

    private void invalidateDistMatrix() {
        distMatrix = null;
    }

    public boolean loadMapData(String filePath) {
        this.dataFilePath = filePath;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            String section = "";
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.equals("[Buildings]")) {
                    section = "Buildings";
                    continue;
                }
                if (line.equals("[Roads]")) {
                    section = "Roads";
                    continue;
                }
                if ("Buildings".equals(section)) {
                    String[] parts = line.split(",", 2);
                    if (parts.length >= 2) {
                        String name = parts[0].trim();
                        buildings.put(name, new Building(name, parts[1].trim()));
                        adjList.putIfAbsent(name, new ArrayList<>());
                    }
                } else if ("Roads".equals(section)) {
                    String[] parts = line.split(",");
                    if (parts.length == 3) {
                        String from = parts[0].trim();
                        String to = parts[1].trim();
                        int dist = Integer.parseInt(parts[2].trim());
                        if (buildings.containsKey(from) && buildings.containsKey(to)) {
                            roads.add(new Road(from, to, dist));
                            adjList.get(from).add(new Edge(to, dist));
                            adjList.get(to).add(new Edge(from, dist));
                        }
                    }
                }
            }
            return true;
        } catch (IOException | NumberFormatException e) {
            System.out.println("加载地图数据失败: " + e.getMessage());
            return false;
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

    private DijkstraResult dijkstra(String start) {
        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        for (String node : buildings.keySet()) {
            dist.put(node, Integer.MAX_VALUE);
        }
        dist.put(start, 0);

        /*
         * 这里用的是优先队列版本的 Dijkstra。
         * 队列里允许同一个节点重复入队，不额外维护 decrease-key；
         * 每次弹出时直接读取当前 dist，如果这条记录已经不是最优值，
         * 后续松弛自然不会生效。这样实现简单，代码量也更少。
         */
        PriorityQueue<String> pq = new PriorityQueue<>(
            Comparator.comparingInt(node -> dist.getOrDefault(node, Integer.MAX_VALUE))
        );
        pq.add(start);

        while (!pq.isEmpty()) {
            String current = pq.poll();
            int currentDist = dist.getOrDefault(current, Integer.MAX_VALUE);
            if (currentDist == Integer.MAX_VALUE) {
                continue;
            }
            for (Edge edge : adjList.getOrDefault(current, Collections.emptyList())) {
                int nextDist = currentDist + edge.weight;
                if (nextDist < dist.getOrDefault(edge.target, Integer.MAX_VALUE)) {
                    dist.put(edge.target, nextDist);
                    prev.put(edge.target, current);
                    pq.add(edge.target);
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

    private List<String> resolveBuildingCandidates(String query) {
        if (query == null || query.isEmpty()) return Collections.emptyList();
        if (buildings.containsKey(query)) return Collections.singletonList(query);
        return searchBuildings(query).stream()
            .map(item -> item.get("name"))
            .filter(Objects::nonNull)
            .distinct()
            .limit(12)
            .collect(Collectors.toList());
    }

    public RouteResult findShortestPath(String start, String end) {
        List<String> startCandidates = resolveBuildingCandidates(start);
        List<String> endCandidates = resolveBuildingCandidates(end);
        if (startCandidates.isEmpty() || endCandidates.isEmpty()) {
            return new RouteResult("起点或终点名称不存在，请重新搜索");
        }

        Map<String, DijkstraResult> dijkstraCache = new HashMap<>();
        List<RouteOption> routeOptions = new ArrayList<>();
        List<String> bestPath = Collections.emptyList();
        List<Integer> bestSegs = Collections.emptyList();
        int bestDist = Integer.MAX_VALUE;

        /*
         * 起点和终点都可能是模糊搜索结果，所以这里不是只算一条路径，
         * 而是枚举所有候选组合。对同一个起点只跑一次 Dijkstra，
         * 这样既能把全部匹配路线列给前端，又能保证地图上展示的是
         * 所有组合里真正最短的那一条。
         */
        for (String startCandidate : startCandidates) {
            DijkstraResult dr = dijkstraCache.computeIfAbsent(startCandidate, this::dijkstra);
            for (String endCandidate : endCandidates) {
                if (!Objects.equals(start, end) && startCandidate.equals(endCandidate)) {
                    continue;
                }
                int dist = dr.dist.getOrDefault(endCandidate, Integer.MAX_VALUE);
                if (dist == Integer.MAX_VALUE) {
                    continue;
                }
                routeOptions.add(new RouteOption(startCandidate, endCandidate, dist));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPath = buildPath(dr.prev, startCandidate, endCandidate);
                    bestSegs = computeSegmentDists(bestPath);
                }
            }
        }

        if (routeOptions.isEmpty()) {
            return new RouteResult("路径不可达");
        }
        routeOptions.sort(Comparator
            .comparingInt((RouteOption option) -> option.totalDist)
            .thenComparing(option -> option.from)
            .thenComparing(option -> option.to));

        RouteResult result = new RouteResult(bestPath, bestSegs, bestDist, false);
        result.matchedStarts = startCandidates;
        result.matchedEnds = endCandidates;
        result.routeOptions = routeOptions;
        return result;
    }

    public RouteResult traverseAll(String start) {
        List<String> startCandidates = resolveBuildingCandidates(start);
        if (startCandidates.isEmpty()) {
            return new RouteResult("起点不存在");
        }
        String resolvedStart = startCandidates.get(0);
        ensureDistMatrix();

        List<String> targets = buildings.values().stream()
            .filter(b -> !"路口".equals(b.desc))
            .map(b -> b.name)
            .collect(Collectors.toList());
        Set<String> unvisited = new LinkedHashSet<>(targets);
        unvisited.remove(resolvedStart);

        List<String> visitOrder = new ArrayList<>();
        List<Integer> segDists  = new ArrayList<>();
        visitOrder.add(resolvedStart);
        int totalDist = 0;
        String current = resolvedStart;

        /*
         * 遍历功能不是精确解 TSP，而是最近邻贪心。
         * 关键点在于“最近”的定义不是图上直接相邻，
         * 而是当前节点到其他建筑的最短路距离。
         * distMatrix 会把每个点的单源最短路结果缓存下来，
         * 这样循环里只需要查表，不用每走一步都重新跑 Dijkstra。
         */
        while (!unvisited.isEmpty()) {
            Map<String, Integer> dists = distMatrix.get(current);
            String nearest = null;
            int nearestDist = Integer.MAX_VALUE;
            for (String node : unvisited) {
                int d = dists.getOrDefault(node, Integer.MAX_VALUE);
                if (d < nearestDist) {
                    nearestDist = d;
                    nearest = node;
                }
            }
            if (nearest == null || nearestDist == Integer.MAX_VALUE) {
                break;
            }
            visitOrder.add(nearest);
            segDists.add(nearestDist);
            totalDist += nearestDist;
            unvisited.remove(nearest);
            current = nearest;
        }
        return new RouteResult(visitOrder, segDists, totalDist, true);
    }

    public List<Map<String, String>> searchBuildings(String query) {
        List<Map<String, String>> exact = new ArrayList<>();
        List<Map<String, String>> prefix = new ArrayList<>();
        List<Map<String, String>> contains = new ArrayList<>();
        for (Building b : buildings.values()) {
            String desc = b.desc == null ? "" : b.desc;
            Map<String, String> item = new HashMap<>();
            item.put("name", b.name);
            item.put("desc", desc);
            if (query == null || query.isEmpty()) {
                exact.add(item);
                continue;
            }
            if (b.name.equals(query)) {
                exact.add(item);
            } else if (b.name.startsWith(query)) {
                prefix.add(item);
            } else if (b.name.contains(query) || desc.contains(query)) {
                contains.add(item);
            }
        }
        List<Map<String, String>> result = new ArrayList<>();
        result.addAll(exact);
        result.addAll(prefix);
        result.addAll(contains);
        return result;
    }

    public synchronized void addBuilding(String name, String desc) throws Exception {
        if (name == null || name.isEmpty()) {
            throw new Exception("名称不能为空");
        }
        if (name.contains(",")) {
            throw new Exception("名称不能包含逗号");
        }
        if (desc != null && desc.contains(",")) {
            throw new Exception("描述不能包含逗号");
        }
        if (buildings.containsKey(name)) {
            throw new Exception("建筑已存在: " + name);
        }
        buildings.put(name, new Building(name, desc));
        adjList.put(name, new ArrayList<>());
        invalidateDistMatrix();
        saveMapData();
    }

    public synchronized void removeBuilding(String name) throws Exception {
        if (!buildings.containsKey(name)) {
            throw new Exception("建筑不存在: " + name);
        }
        buildings.remove(name);
        adjList.remove(name);
        roads.removeIf(r -> r.from.equals(name) || r.to.equals(name));
        for (List<Edge> edges : adjList.values()) {
            edges.removeIf(e -> e.target.equals(name));
        }
        invalidateDistMatrix();
        saveMapData();
    }

    public synchronized void addRoad(String from, String to, int dist) throws Exception {
        if (!buildings.containsKey(from)) {
            throw new Exception("起点不存在: " + from);
        }
        if (!buildings.containsKey(to)) {
            throw new Exception("终点不存在: " + to);
        }
        for (Road r : roads) {
            if ((r.from.equals(from) && r.to.equals(to)) || (r.from.equals(to) && r.to.equals(from))) {
                throw new Exception("该道路已存在");
            }
        }
        roads.add(new Road(from, to, dist));
        adjList.get(from).add(new Edge(to, dist));
        adjList.get(to).add(new Edge(from, dist));
        invalidateDistMatrix();
        saveMapData();
    }

    public synchronized void removeRoad(String from, String to) throws Exception {
        boolean ok = roads.removeIf(r ->
            (r.from.equals(from) && r.to.equals(to)) || (r.from.equals(to) && r.to.equals(from)));
        if (!ok) {
            throw new Exception("道路不存在");
        }
        adjList.getOrDefault(from, Collections.emptyList()).removeIf(e -> e.target.equals(to));
        adjList.getOrDefault(to, Collections.emptyList()).removeIf(e -> e.target.equals(from));
        invalidateDistMatrix();
        saveMapData();
    }

    public void startServer(int port, String dataDir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            serveFile(exchange, new File(dataDir, "index.html"), "text/html; charset=UTF-8");
        });
        server.createContext("/annotate", exchange ->
            serveFile(exchange, new File(dataDir, "annotate.html"), "text/html; charset=UTF-8"));
        server.createContext("/map.jpg", exchange ->
            serveFile(exchange, new File(dataDir, "北邮本部地图.jpg"), "image/jpeg"));

        server.createContext("/api/buildings", exchange -> {
            addCorsHeaders(exchange);
            String query = getQueryParam(exchange, "q");
            List<Map<String, String>> results = searchBuildings(query);
            if (query != null && !query.isEmpty() && results.size() > 12) {
                results = results.subList(0, 12);
            }
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
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/api/building", exchange -> {
            addCorsHeaders(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = readBody(exchange);
            String action = extractJsonString(body, "action");
            try {
                if ("add".equals(action)) {
                    addBuilding(extractJsonString(body, "name"), extractJsonString(body, "desc"));
                } else if ("delete".equals(action)) {
                    removeBuilding(extractJsonString(body, "name"));
                } else {
                    throw new Exception("未知操作");
                }
                sendJson(exchange, "{\"ok\":true}");
            } catch (Exception e) {
                sendJson(exchange, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        server.createContext("/api/road", exchange -> {
            addCorsHeaders(exchange);
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body = readBody(exchange);
            String action = extractJsonString(body, "action");
            try {
                if ("add".equals(action)) {
                    Integer dist = extractJsonInt(body, "dist");
                    if (dist == null || dist <= 0) {
                        throw new Exception("距离必须为正整数");
                    }
                    addRoad(extractJsonString(body, "from"), extractJsonString(body, "to"), dist);
                } else if ("delete".equals(action)) {
                    removeRoad(extractJsonString(body, "from"), extractJsonString(body, "to"));
                } else {
                    throw new Exception("未知操作");
                }
                sendJson(exchange, "{\"ok\":true}");
            } catch (Exception e) {
                sendJson(exchange, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("服务器已启动: http://localhost:" + port);
        System.out.println("标注工具: http://localhost:" + port + "/annotate");
    }

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
        if (r.error != null) {
            return "{\"error\":\"" + escapeJson(r.error) + "\"}";
        }
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
        sb.append("]");
        if (r.matchedStarts != null) {
            sb.append(",\"matchedStarts\":[");
            for (int i = 0; i < r.matchedStarts.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(r.matchedStarts.get(i))).append("\"");
            }
            sb.append("]");
        }
        if (r.matchedEnds != null) {
            sb.append(",\"matchedEnds\":[");
            for (int i = 0; i < r.matchedEnds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(r.matchedEnds.get(i))).append("\"");
            }
            sb.append("]");
        }
        if (r.routeOptions != null) {
            sb.append(",\"routeOptions\":[");
            for (int i = 0; i < r.routeOptions.size(); i++) {
                RouteOption option = r.routeOptions.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"from\":\"").append(escapeJson(option.from))
                    .append("\",\"to\":\"").append(escapeJson(option.to))
                    .append("\",\"totalDist\":").append(option.totalDist)
                    .append("}");
            }
            sb.append("]");
        }
        return sb.append("}").toString();
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        idx += search.length();
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '"') {
                break;
            }
            if (c == '\\' && idx + 1 < json.length()) {
                char next = json.charAt(++idx);
                sb.append(next == 'n' ? '\n' : next);
            } else {
                sb.append(c);
            }
            idx++;
        }
        return sb.toString();
    }

    private Integer extractJsonInt(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        idx += search.length();
        while (idx < json.length() && json.charAt(idx) == ' ') {
            idx++;
        }
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == idx) {
            return null;
        }
        return Integer.parseInt(json.substring(idx, end));
    }

    private static String resolveDataDir(String[] args) {
        if (args.length > 0 && new File(args[0]).isDirectory()) {
            return args[0];
        }
        if (new File("java/test.txt").exists()) {
            return "java";
        }
        try {
            File loc = new File(CampusNavSystem.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            File dir = loc.isFile() ? loc.getParentFile() : loc;
            for (int i = 0; i < 6; i++) {
                File candidate = new File(dir, "java");
                if (new File(candidate, "test.txt").exists()) {
                    return candidate.getAbsolutePath();
                }
                File parent = dir.getParentFile();
                if (parent == null) {
                    break;
                }
                dir = parent;
            }
        } catch (Exception ignored) {
        }
        return "java";
    }

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
        System.out.println("数据目录: " + new File(dataDir).getAbsolutePath());
        if (!system.loadMapData(dataDir + "/test.txt")) {
            System.out.println("找不到数据文件，请用 run.sh / run.bat 启动，或在项目根目录执行。");
            return;
        }
        system.startServer(8080, dataDir);
        openBrowser("http://localhost:8080");
        System.out.println("按 Ctrl+C 退出服务器");
    }
}
