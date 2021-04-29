import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.*;

public class Main {
    private static final Charset charset = Charset.forName("cp866");

    public static void main(String[] args) throws Exception {
        Process hostName = Runtime.getRuntime().exec("hostname");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(hostName.getInputStream(), charset))) {
            System.out.println("Hostname: " + r.readLine());
        }

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces.hasMoreElements()) {
            String line = "------------------------------";
            System.out.append('\n').println(line);
            do {
                NetworkInterface ni = interfaces.nextElement();
                byte[] hardwareAddress = ni.getHardwareAddress();
                if (hardwareAddress != null) {
                    StringJoiner sj = new StringJoiner(".");
                    for (byte v : hardwareAddress) {
                        String s = Integer.toHexString(v);
                        int l = s.length();
                        s = l > 2 ? s.substring(l - 2) : s;
                        sj.add(s.toUpperCase());
                    }
                    System.out.println("Name:" + ni.getDisplayName());
                    System.out.println("Mac: " + sj.toString());
                    System.out.println(line);
                }
            } while (interfaces.hasMoreElements());
        } else {
            System.out.println("Noting");
        }

        List<String> lines = readArpTable();
        lines.removeIf(s -> !s.contains("динамический"));
        if (lines.isEmpty())
            return;
        ArrayList<Callable<Void>> callables = new ArrayList<>();
        for (String s : lines) {
            Callable<Void> callable = () -> {
                String[] array = s.trim().split("\\s+");
                String ipAddress = array[0];
                String macAddress = array[1];
                try {
                    String nodeName = sendNsLookupRequest(ipAddress);
                    System.out.println("Network node IP address: " + ipAddress +
                            ", MacAddress:" + macAddress +
                            ", hostname:" + nodeName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            };
            callables.add(callable);
        }

        System.out.println("\nNetwork nodes:");
        ExecutorService executor = Executors.newFixedThreadPool(5);
        executor.invokeAll(callables);
        awaitTerminationAfterShutdown(executor);
    }

    private static List<String> readArpTable() throws IOException {
        Process arpProcess = Runtime.getRuntime().exec("arp -a");
        ArrayList<String> list = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(arpProcess.getInputStream(), charset))) {
            String s;
            while ((s = r.readLine()) != null)
                list.add(s);
        } finally {
            arpProcess.destroy();
        }
        return list;
    }
    private static String sendNsLookupRequest(String ipAddress) throws IOException, ExecutionException, InterruptedException {
        CompletableFuture<Process> future = Runtime.getRuntime().exec("nslookup " + ipAddress).onExit();
        Process nsLookupProcess = future.get();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(nsLookupProcess.getInputStream(), charset))) {
            String hostnameLine = r.readLine();
            return hostnameLine.substring(hostnameLine.lastIndexOf(':') + 1).trim();
        } finally {
            nsLookupProcess.destroy();
        }
    }
    public static void awaitTerminationAfterShutdown(ExecutorService threadPool) {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}