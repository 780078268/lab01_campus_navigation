import java.util.*;

public class CampusNavigation {
    // 建筑实体类
    static class Building {
        String name;
        String desc;
        public Building(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    // 边实体类
    static class Edge {
        String to;
        int weight;
        public Edge(String to, int weight) {
            this.to = to;
            this.weight = weight;
        }
    }

    private Map<String, Building> buildings = new HashMap<>();
    private Map<String, List<Edge>> adj = new HashMap<>();

    // 核心功能：Dijkstra 算法
    public void getShortestPath(String start, String end) {
        if (!buildings.containsKey(start) || !buildings.containsKey(end)) {
            System.out.println("输入的建筑名称有误！");
            return;
        }

        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>(); // 记录前驱节点，用于打印路径
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingInt(dist::get));

        for (String name : buildings.keySet()) {
            dist.put(name, Integer.MAX_VALUE);
        }
        dist.put(start, 0);
        pq.add(start);

        while (!pq.isEmpty()) {
            String curr = pq.poll();
            if (curr.equals(end)) break;

            for (Edge edge : adj.getOrDefault(curr, new ArrayList<>())) {
                int newDist = dist.get(curr) + edge.weight;
                if (newDist < dist.get(edge.to)) {
                    dist.put(edge.to, newDist);
                    prev.put(edge.to, curr);
                    pq.add(edge.to);
                }
            }
        }

        printResult(start, end, dist.get(end), prev);
    }

    private void printResult(String start, String end, int distance, Map<String, String> prev) {
        if (distance == Integer.MAX_VALUE) {
            System.out.println("对不起，从 " + start + " 到 " + end + " 没路可走。");
            return;
        }

        // 路径回溯
        List<String> path = new ArrayList<>();
        for (String at = end; at != null; at = prev.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);

        System.out.println("\n【导航详情】学号: 2024213672");
        System.out.println("目标：" + buildings.get(end).name + " (" + buildings.get(end).desc + ")");
        System.out.println("全程距离：" + distance + "米");
        System.out.print("推荐路线：");
        for (int i = 0; i < path.size(); i++) {
            System.out.print(path.get(i) + (i == path.size() - 1 ? "" : " -> "));
        }
        System.out.println();
    }
}