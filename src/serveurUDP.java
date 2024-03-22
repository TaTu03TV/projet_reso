package src;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class serveurUDP implements Runnable {
    private static Map<Integer, List<ClientInfo>> rooms = new HashMap<>();
    private final int roomNumber;
    private static DatagramSocket server;
    private static final long TIMEOUT_INTERVAL = 10000; // Increased timeout interval for testing (10 seconds)

    public serveurUDP(int roomNumber) {
        this.roomNumber = roomNumber;
    }

    public static void main(String[] args) {
        System.out.println("Serveur UDP started");
        try {
            server = new DatagramSocket(2345, InetAddress.getLocalHost());
            while (true) {
                byte[] buffer = new byte[8192];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                server.receive(packet);
                packet.setLength(buffer.length);

                String str = new String(packet.getData()).trim();
                if (!str.startsWith("FETCH")) {
                    System.out.println("Received message: " + str);
                }

                if (str.startsWith("ROOM")) {
                    int roomNumber = Integer.parseInt(str.split(" ")[1]);
                    rooms.computeIfAbsent(roomNumber, k -> new ArrayList<>())
                            .add(new ClientInfo(packet.getAddress(), packet.getPort(), System.currentTimeMillis()));
                    System.out.println("Client added to room " + roomNumber);
                    serveurUDP roomServer = new serveurUDP(roomNumber);
                    Thread roomThread = new Thread(roomServer); // on créé la room dans un thread a part
                    roomThread.start();
                } else if (str.startsWith("MSG")) {
                    int roomNumber = Integer.parseInt(str.split(" ")[1]);
                    String message = str.split(" ", 3)[2];
                    for (ClientInfo client : rooms.get(roomNumber)) { // pour le timeout
                        if (client.getAddress().equals(packet.getAddress()) && client.getPort() == packet.getPort()) {
                            client.setLastActiveTime(System.currentTimeMillis());
                            continue;
                        }
                        byte[] msgBuffer = message.getBytes();
                        DatagramPacket msgPacket = new DatagramPacket(msgBuffer, msgBuffer.length, client.getAddress(),
                                client.getPort());
                        server.send(msgPacket);
                    }
                } else if (str.startsWith("FETCH")) {
                    int roomNumber = Integer.parseInt(str.split(" ")[1]);
                    sendMessagesToClient(server, roomNumber, packet.getAddress(), packet.getPort());
                } else if (str.startsWith("MSG EXIT")) {
                    System.out.println("Client asked to leave room");
                    // we close the connexion of the client and remove it from the room
                    int roomNumber = Integer.parseInt(str.split(" ")[1]);

                    for (ClientInfo client : rooms.get(roomNumber)) {
                        if (client.getAddress().equals(packet.getAddress()) && client.getPort() == packet.getPort()) {
                            rooms.get(roomNumber).remove(client);
                            break;
                        }
                    }
                    if (rooms.get(roomNumber).isEmpty()) {
                        rooms.remove(roomNumber);
                    }
                }

                checkClientTimeouts(); // on regarde si un client est inactif
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (server != null) {
                server.close(); // on libère le socket
            }
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("Server is running");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendMessagesToClient(DatagramSocket server, int roomNumber, InetAddress clientAddress,
            int clientPort) throws IOException {
        if (rooms.containsKey(roomNumber)) {
            StringBuilder messages = new StringBuilder();
            byte[] msgBuffer = messages.toString().getBytes();
            DatagramPacket msgPacket = new DatagramPacket(msgBuffer, msgBuffer.length, clientAddress, clientPort);
            server.send(msgPacket);
        }
    }

    private static class ClientInfo {
        private InetAddress address;
        private int port;
        private long lastActiveTime;

        public ClientInfo(InetAddress address, int port, long lastActiveTime) {
            this.address = address;
            this.port = port;
            this.lastActiveTime = lastActiveTime;
        }

        public InetAddress getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }

        public long getLastActiveTime() {
            return lastActiveTime;
        }

        public void setLastActiveTime(long lastActiveTime) {
            this.lastActiveTime = lastActiveTime;
        }
    }

    private static void checkClientTimeouts() {
        long currentTime = System.currentTimeMillis();
        Iterator<Integer> roomIterator = rooms.keySet().iterator();
        while (roomIterator.hasNext()) {
            Integer roomNumber = roomIterator.next();
            System.out.println("Checking room " + roomNumber + " for inactive clients");
            List<ClientInfo> clients = rooms.get(roomNumber);
            if (clients == null) {
                roomIterator.remove(); // Remove the room if it no longer exists
                continue;
            }
            
            Iterator<ClientInfo> clientIterator = clients.iterator();
            while (clientIterator.hasNext()) {
                ClientInfo client = clientIterator.next();
                if (currentTime - client.getLastActiveTime() > TIMEOUT_INTERVAL) {
                    clientIterator.remove();
                    System.out.println("Client " + client.getAddress() + ":" + client.getPort() + " timed out");
                    
                }
            }
    
            if (clients.isEmpty()) {
                roomIterator.remove();
                System.out.println("Room " + roomNumber + " became empty and was removed");
            }
        }
    }
    
}
