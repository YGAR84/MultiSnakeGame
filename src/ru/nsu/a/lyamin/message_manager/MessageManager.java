package ru.nsu.a.lyamin.message_manager;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.nsu.a.lyamin.message_decoder.SnakesProto;
import ru.nsu.a.lyamin.num_sequence.NumSequenceGenerator;
import ru.nsu.a.lyamin.snake_game.SnakeGame;
import ru.nsu.a.lyamin.view.ErrorBox;
import ru.nsu.a.lyamin.view.GameWindow;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class MessageManager
{
    private byte[] buffer = new byte[8192];
    private DatagramSocket socket;

    private SnakeGame snakeGame;

    private GameWindow gameWindow;

    //message -> hostInfo
//    private ConcurrentHashMap<Long, SnakesProto.GameMessage> messagesIds = new ConcurrentHashMap<>();
//    private ConcurrentHashMap<SnakesProto.GameMessage, HostInfo> messages = new ConcurrentHashMap<>();

    private ConcurrentHashMap<HostInfo, ConcurrentHashMap<Long, SnakesProto.GameMessage>> messages
                                                                                    = new ConcurrentHashMap<>();

    private ConcurrentHashMap<HostInfo, ConcurrentHashMap<Long, SnakesProto.GameMessage>> gameMessages
            = new ConcurrentHashMap<>();

    private ConcurrentHashMap<Integer, HostInfo> playersIds = new ConcurrentHashMap<>();
    private NumSequenceGenerator numSequenceGenerator = new NumSequenceGenerator();


    private ConcurrentHashMap<HostInfo, SnakesProto.NodeRole> allPlayers = new ConcurrentHashMap<>();

    private HostInfo master = null;

    private int pingDelay;
    private int nodeTimeout;
    private int stateDelay;

    private Timer sender;
    private Thread reciever;

    private SnakesProto.NodeRole nodeRole;

    private ConcurrentHashMap<HostInfo, HashSet<Long>> lastIds = new ConcurrentHashMap<>();
//    private HashSet<Long> lastIds = new HashSet<>();

    public MessageManager(SnakeGame _snakeGame, SnakesProto.GameMessage.AnnouncementMsg announcementMsg,
                          HostInfo master, SnakesProto.NodeRole _nodeRole)
    {
        nodeRole = _nodeRole;

        snakeGame = _snakeGame;

        for(SnakesProto.GamePlayer gamePlayer : announcementMsg.getPlayers().getPlayersList())
        {
            if(gamePlayer.getIpAddress().equals(""))
            {
                allPlayers.put(master, gamePlayer.getRole());
                continue;
            }

            try
            {
                allPlayers.put(new HostInfo(InetAddress.getByName(gamePlayer.getIpAddress()), gamePlayer.getPort()),
                        gamePlayer.getRole());
            }
            catch (UnknownHostException e)
            {
                e.printStackTrace();
            }
        }

        Init(announcementMsg.getConfig());
    }

    public MessageManager(SnakeGame _snakeGame, SnakesProto.GameConfig gameConfig, SnakesProto.NodeRole _nodeRole)
    {

        nodeRole = _nodeRole;
//        pingDelay = gameConfig.getPingDelayMs();
//        nodeTimeout = gameConfig.getNodeTimeoutMs();
//        stateDelay = gameConfig.getStateDelayMs();

        snakeGame = _snakeGame;

        Init(gameConfig);
//        try
//        {
//            socket = new DatagramSocket();
//        }
//        catch (IOException e)
//        {
//            e.printStackTrace();
//        }
//
//        reciever = new Thread(() ->
//        {
//            while(!Thread.interrupted())
//            {
//                try
//                {
//                    DatagramPacket dp = new DatagramPacket(buffer, 8192);
//                    socket.receive(dp);
//                    proccessedMessage(dp);
//
//                }
//                catch (IOException e)
//                {
//                    e.printStackTrace();
//                }
//            }
//        });
//
//        reciever.start();
//
//        sender = new Timer();
//        sender.scheduleAtFixedRate(new TimerTask()
//        {
//            @Override
//            public void run()
//            {
//
//                for(Map.Entry<HostInfo, SnakesProto.GameMessage> entry : messages.entrySet())
//                {
//                    byte[] mess = entry.getValue().toByteArray();
//                    try
//                    {
//                        socket.send(new DatagramPacket(mess, 0, mess.length, entry.getKey().getIp(), entry.getKey().getPort()));
//                    }
//                    catch (IOException e)
//                    {
//                        e.printStackTrace();
//                    }
//                }
//
//            }
//        }, 0, pingDelay);

    }

    private void Init(SnakesProto.GameConfig gameConfig)
    {
        pingDelay = gameConfig.getPingDelayMs();
        nodeTimeout = gameConfig.getNodeTimeoutMs();
        stateDelay = gameConfig.getStateDelayMs();

        try
        {
            socket = new DatagramSocket();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        reciever = new Thread(() ->
        {
            while(!Thread.interrupted())
            {
                try
                {
                    DatagramPacket dp = new DatagramPacket(buffer, 8192);
                    socket.receive(dp);
                    proccessedMessage(dp);

                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });

        reciever.start();

        sender = new Timer();
        sender.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                System.out.println("_________________MESSAGES TO SEND_________________");
                //System.out.println("Total messages to send: " + messages.size());
                for(Map.Entry<HostInfo, ConcurrentHashMap<Long, SnakesProto.GameMessage>> firstEntry : messages.entrySet())
                {
                    System.out.println("MESSAGES FOR: " + firstEntry.getKey().getIp() + " ; " + firstEntry.getKey().getPort() + ":");
                    for(Map.Entry<Long, SnakesProto.GameMessage> secondEntry : firstEntry.getValue().entrySet())
                    {
                        System.out.println("\t " + secondEntry.getValue() + "\n");
                        byte[] mess = secondEntry.getValue().toByteArray();
                        try
                        {
                            socket.send(new DatagramPacket(mess, 0, mess.length,
                                    firstEntry.getKey().getIp(), firstEntry.getKey().getPort()));
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }

                    }
                }
                System.out.println("_________________MESSAGES TO SEND_________________");

            }
        }, 0, pingDelay);
    }


    // TODO: some shit with ids
    private void proccessedMessage(DatagramPacket dp)
    {
        try
        {
            byte messBytes[] = new byte[dp.getLength()];

            System.arraycopy(dp.getData(), 0, messBytes, 0, dp.getLength());

            SnakesProto.GameMessage mess = SnakesProto.GameMessage.parseFrom(messBytes);

            //System.out.println("RECIVIED FROM: " + dp.getPort());

            HostInfo sender = new HostInfo(dp.getAddress(), dp.getPort());

            if(mess.hasPing())
            {
                //System.out.println("RECEIVED PING");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new HashSet<>());
                    }

                    lastIds.get(sender).add(mess.getMsgSeq());
                }

                sendAck(mess, sender);

            }
            else if(mess.hasSteer())
            {
                System.out.println("RECEIVED STEER");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new HashSet<>());
                    }

                    lastIds.get(sender).add(mess.getMsgSeq());

                    snakeGame.changeSnakeDir(mess.getSenderId(), mess.getSteer().getDirection());
                }

                sendAck(mess, sender);

            }
            else if(mess.hasAck())
            {
                //System.out.println("RECEIVED ACK");
                //System.out.println(mess.getMsgSeq());
                //remove from send some how
                //if(lastIds.contains(mess.getMsgSeq())) return;

                if(!messages.containsKey(sender)) return;

                if(!messages.get(sender).containsKey(mess.getMsgSeq())) return;

                SnakesProto.GameMessage messThatAcked = messages.get(sender).get(mess.getMsgSeq());
//
//                System.out.println(messThatAcked);
//
//
//
                if(messThatAcked.hasJoin())
                {
                    master = sender;
                    allPlayers.put(sender, SnakesProto.NodeRole.MASTER);
                    snakeGame.getGameWindow().setPi(mess.getReceiverId());

                    SnakesProto.GameMessage ack = createAck(mess);

                    if(messages.get(sender) == null)
                    {
                        messages.put(sender, new ConcurrentHashMap<>());
                    }

                    messages.get(sender).put(ack.getMsgSeq(), ack);


                }

                messages.get(sender).remove(mess.getMsgSeq());

            }
            else if(mess.hasState())
            {
                //System.out.println("RECEIVED STATE");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new HashSet<>());
                    }

                    lastIds.get(sender).add(mess.getMsgSeq());

                    SnakesProto.GameState gameState = mess.getState().getState();
                    snakeGame.loadState(gameState);
                    snakeGame.getGameWindow().repaint();
                }

                sendAck(mess, sender);

            }
            else if(mess.hasJoin())
            {
                //System.out.println("RECEIVED JOIN");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new HashSet<>());
                    }

                    lastIds.get(sender).add(mess.getMsgSeq());

                    SnakesProto.GameMessage.JoinMsg joinMsg = mess.getJoin();


                    SnakesProto.PlayerType newPlayerType = SnakesProto.PlayerType.HUMAN;
                    if(joinMsg.hasPlayerType())
                    {
                        newPlayerType = joinMsg.getPlayerType();
                    }


                    SnakesProto.NodeRole newNodeRole = SnakesProto.NodeRole.VIEWER;
                    if(!joinMsg.hasOnlyView() || !joinMsg.getOnlyView())
                    {
                        // TODO: Deputy Mechanic

                        newNodeRole = SnakesProto.NodeRole.NORMAL;

                    }

                    int newPlayerId = snakeGame.addPlayer(mess.getJoin().getName(),
                            newNodeRole, newPlayerType, sender.getIp().toString(), sender.getPort());

                    if(messages.get(sender) == null)
                    {
                        messages.put(sender, new ConcurrentHashMap<>());
                    }

                    if(newPlayerId == -1)
                    {
                        SnakesProto.GameMessage errorMes = SnakesProto.GameMessage.newBuilder()
                                .setError(SnakesProto.GameMessage.ErrorMsg.newBuilder()
                                        .setErrorMessage("No place for new"))
                                .build();

                        messages.get(sender).put(numSequenceGenerator.getNextNum(), errorMes);
                    }
                    else
                    {

                        SnakesProto.GameMessage ack = SnakesProto.GameMessage.newBuilder()
                                .setMsgSeq(mess.getMsgSeq())
                                .setReceiverId(newPlayerId)
                                .setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build())
                                .build();

                        byte[] ackByte = ack.toByteArray();
                        DatagramPacket ackDp = new DatagramPacket(ackByte, 0, ackByte.length,
                                    sender.getIp(), sender.getPort());

                        try
                        {
                            socket.send(ackDp);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }


                        System.out.println("Put ack");
                        messages.get(sender).put(ack.getMsgSeq(), ack);

                        allPlayers.put(sender, SnakesProto.NodeRole.NORMAL);

                        playersIds.put(newPlayerId, sender);
                    }
                }
                else
                {

                }
            }
            else if(mess.hasError())
            {
                //System.out.println("RECEIVED ERROR");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new HashSet<>());
                    }

                    lastIds.get(sender).add(mess.getMsgSeq());

                    ErrorBox.display(mess.getError().getErrorMessage());
                }

                sendAck(mess, sender);
            }
            else if(mess.hasRoleChange())
            {
                //System.out.println("RECEIVED ROLE CHANGE");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new HashSet<>());
                    }

                    lastIds.get(sender).add(mess.getMsgSeq());


                    if(mess.hasReceiverId() && mess.hasSenderId())
                    {
                        SnakesProto.GameMessage.RoleChangeMsg roleChangeMsg = mess.getRoleChange();

                        if(playersIds.containsKey(mess.getReceiverId()) && playersIds.containsKey(mess.getSenderId()))
                        {
                            HostInfo messReceiver = playersIds.get(mess.getReceiverId());
                            HostInfo messSender = playersIds.get(mess.getSenderId());

                            allPlayers.put(messReceiver, roleChangeMsg.getReceiverRole());
                            allPlayers.put(messSender, roleChangeMsg.getSenderRole());
                        }
                    }
                }


                sendAck(mess, sender);
            }

        }
        catch (InvalidProtocolBufferException e)
        {
            e.printStackTrace();
        }
    }

    private SnakesProto.GameMessage createAck(SnakesProto.GameMessage gameMessage)
    {
        return SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(gameMessage.getMsgSeq())
                .setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build())
                .build();
    }

    public int getMyPort()
    {
        return socket.getLocalPort();
    }

    public void sendSteer(int senderId, SnakesProto.Direction dir)
    {
        if(master == null) return;

        SnakesProto.GameMessage steerMessage = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(numSequenceGenerator.getNextNum())
                .setSteer(SnakesProto.GameMessage.SteerMsg
                        .newBuilder()
                        .setDirection(dir))
                .setSenderId(senderId)
                .build();

//        System.out.println("SEND STEER");

 //       System.out.println("STEER MSG: " + steerMessage);

        if(!messages.containsKey(master))
        {
            messages.put(master, new ConcurrentHashMap<>());
        }

//        System.out.println(messages.get(master).get(steerMessage.getMsgSeq()));

        byte[] steerMsg = steerMessage.toByteArray();
        DatagramPacket steerDp = new DatagramPacket(steerMsg, 0, steerMsg.length,
                master.getIp(), master.getPort());

 //       System.out.println("MY MASTER: " + master.getIp() + " " + master.getPort());

        try
        {
            socket.send(steerDp);
//            System.out.println("SEND STEER");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        messages.get(master).put(steerMessage.getMsgSeq(), steerMessage);

        System.out.println("Steer in map \\_/ ");
        System.out.println(messages.get(master).get(steerMessage.getMsgSeq()));
    }

    public void sendJoin(HostInfo hi, String name)
    {
        SnakesProto.GameMessage joinMsg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(numSequenceGenerator.getNextNum())
                .setJoin(SnakesProto.GameMessage.JoinMsg
                        .newBuilder()
                        .setName(name))
                .build();

        byte[] joinMsgByte = joinMsg.toByteArray();
        DatagramPacket dp = new DatagramPacket(joinMsgByte, 0, joinMsgByte.length,
                hi.getIp(), hi.getPort());

        try
        {
            socket.send(dp);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if(!messages.containsKey(hi))
        {
            messages.put(hi, new ConcurrentHashMap<>());
        }

        messages.get(hi).put(joinMsg.getMsgSeq(), joinMsg);
    }

    public void sendRoleChange()
    {

    }

    public void disableMessageManager()
    {
        reciever.interrupt();
        sender.cancel();
        socket.close();
    }


    public void sendState()
    {
        SnakesProto.GameState gameState = snakeGame.generateNewState();
        SnakesProto.GameMessage.StateMsg.Builder stateMsgBuilder = SnakesProto.GameMessage.StateMsg.newBuilder()
                .setState(gameState);

        for(HostInfo hi : allPlayers.keySet())
        {
            SnakesProto.GameMessage stateMsg = SnakesProto.GameMessage.newBuilder()
                    .setState(stateMsgBuilder)
                    .setMsgSeq(numSequenceGenerator.getNextNum())
                    .build();

            messages.get(hi).put(stateMsg.getMsgSeq(), stateMsg);
        }
    }

    public HostInfo getHostInfo(int pi)
    {

//        System.out.println("_______________PLAYERS_IDS________________");
//        for(Map.Entry<Integer, HostInfo> entry: playersIds.entrySet())
//        {
//            System.out.println("ID: "+ entry.getKey() + "\nIP: " + entry.getValue().getIp() + "\nPORT: " + entry.getValue().getPort());
//        }
//        System.out.println("_______________PLAYERS_IDS________________");
        return playersIds.get(pi);
    }

    public int addMe(String name, SnakesProto.NodeRole _nodeRole, SnakesProto.PlayerType _playerType)
    {
        int newId = snakeGame.addPlayer(name, _nodeRole, _playerType, "", socket.getLocalPort());
        if(newId > 0)
        {
            playersIds.put(newId, new HostInfo(socket.getLocalAddress(),socket.getLocalPort()));
        }

        return newId;
    }

    private void sendAck(SnakesProto.GameMessage gameMessage, HostInfo hi)
    {
        SnakesProto.GameMessage ack = createAck(gameMessage);

        byte[] ackByte = ack.toByteArray();

        DatagramPacket dp = new DatagramPacket(ackByte, 0, ackByte.length, hi.getIp(), hi.getPort());

        try
        {
            socket.send(dp);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
