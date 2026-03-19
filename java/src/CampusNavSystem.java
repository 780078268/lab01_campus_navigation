import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * 数据结构课程设计 - 校园导航系统
 * 开发者: 2024213672 李韶庸
 */
public class CampusNavSystem {

    // 1. 定义地点类
    static class Building {
        String name;
        String desc;

        public Building(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    // 2. 定义边类（道路）
    static class Edge {
        String target;
        int weight;

        public Edge(String target, int weight) {
            this.target = target;
            this.weight = weight;
        }
    }

    private final Map<String, Building> buildings = new HashMap<>(); // 顶点表
    private final Map<String, List<Edge>> adjList = new HashMap<>();   // 邻接表

    // 3. 加载 test.txt 数据文件
    public boolean loadMapData(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            String currentSection = "";

            while ((line = br.readLine()) != null) {
                line = line.trim();
                // 过滤注释和空行
                if (line.isEmpty() || line.startsWith("#")) continue;

                // 识别区块标签
                if (line.equals("[Buildings]")) {
                    currentSection = "Buildings";
                    continue;
                } else if (line.equals("[Roads]")) {
                    currentSection = "Roads";
                    continue;
                }

                if ("Buildings".equals(currentSection)) {
                    // 解析: 名称,描述
                    String[] parts = line.split(",", 2);
                    if (parts.length >= 2) {
                        String name = parts[0].trim();
                        String desc = parts[1].trim();
                        buildings.put(name, new Building(name, desc));
                        adjList.putIfAbsent(name, new ArrayList<>());
                    }
                } else if ("Roads".equals(currentSection)) {
                    // 解析: 起点,终点,距离
                    String[] parts = line.split(",");
                    if (parts.length == 3) {
                        String u = parts[0].trim();
                        String v = parts[1].trim();
                        int dist = Integer.parseInt(parts[2].trim());

                        // 确保两个点都已在 [Buildings] 中定义
                        if (buildings.containsKey(u) && buildings.containsKey(v)) {
                            adjList.get(u).add(new Edge(v, dist));
                            adjList.get(v).add(new Edge(u, dist));
                        }
                    }
                }
            }
            System.out.println("✅ 数据加载成功！当前系统包含地点总数: " + buildings.size());
            return true;
        } catch (IOException | NumberFormatException e) {
            System.out.println("❌ 地图文件加载异常: " + e.getMessage());
            return false;
        }
    }

    // 4. Dijkstra 最短路径算法核心实现
    public void findShortestPath(String start, String end) {
        if (!buildings.containsKey(start) || !buildings.containsKey(end)) {
            System.out.println("❌ 错误：输入的起点或终点名称不匹配，请检查 test.txt。");
            return;
        }

        Map<String, Integer> dist = new HashMap<>(); // 存储到起点的距离
        Map<String, String> prev = new HashMap<>(); // 存储路径前驱
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingInt(dist::get));

        // 初始化
        for (String name : buildings.keySet()) {
            dist.put(name, Integer.MAX_VALUE);
        }
        dist.put(start, 0);
        pq.add(start);

        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (u.equals(end)) break;

            for (Edge edge : adjList.getOrDefault(u, new ArrayList<>())) {
                int alt = dist.get(u) + edge.weight;
                if (alt < dist.get(edge.target)) {
                    dist.put(edge.target, alt);
                    prev.put(edge.target, u);
                    pq.add(edge.target);
                }
            }
        }

        printResult(start, end, dist.get(end), prev);
    }

    // 5. 格式化输出导航结果
    private void printResult(String start, String end, int totalDist, Map<String, String> prev) {
        if (totalDist == Integer.MAX_VALUE) {
            System.out.println("❌ 抱歉，路径不可达。");
            return;
        }

        List<String> path = new ArrayList<>();
        for (String at = end; at != null; at = prev.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);

        System.out.println("\n------------------------------------------");
        System.out.println("📍 校园导航系统 (开发者: 李韶庸)");
        System.out.println("------------------------------------------");
        System.out.println("🏁 目的地：" + end + " [" + buildings.get(end).desc + "]");
        System.out.println("📏 最短路径长度：" + totalDist + " 米");
        System.out.println("🚶 建议路线：");

        for (int i = 0; i < path.size(); i++) {
            System.out.print(path.get(i));
            if (i < path.size() - 1) {
                System.out.print(" -> ");
                if ((i + 1) % 5 == 0) System.out.println(); // 每5个点换行，保持美观
            }
        }
        System.out.println("\n------------------------------------------\n");
    }

    public static void main(String[] args) {
        CampusNavSystem system = new CampusNavSystem();
        Scanner sc = new Scanner(System.in);

        // 请确保 test.txt 与 java 文件在同一目录下
        if (!system.loadMapData("test.txt")) return;

        while (true) {
            System.out.println("请输入操作编号：[1]最短路径查询  [0]退出");
            String cmd = sc.nextLine();

            if ("0".equals(cmd)) break;
            if ("1".equals(cmd)) {
                System.out.print("请输入起点建筑/路口名: ");
                String from = sc.nextLine().trim();
                System.out.print("请输入终点建筑/路口名: ");
                String to = sc.nextLine().trim();
                system.findShortestPath(from, to);
            }
        }
        sc.close();
    }
}