package ru.nsu.a.lyamin.message_manager;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.nsu.a.lyamin.message_decoder.SnakesProto;
import ru.nsu.a.lyamin.num_sequence.NumSequenceGenerator;
import ru.nsu.a.lyamin.snake_game.SnakeGame;
import ru.nsu.a.lyamin.view.ErrorBox;
import ru.nsu.a.lyamin.view.GameWindow;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessageManager
{
    private byte[] buffer = new byte[8192];
    private DatagramSocket socket;

    private SnakeGame snakeGame;

    private GameWindow gameWindow;

    private ConcurrentHashMap<HostInfo, ConcurrentHashMap<Long, SnakesProto.GameMessage>> messages
                                            = new ConcurrentHashMap<>();

    private ConcurrentHashMap<HostInfo, Long> nodesTimeout = new ConcurrentHashMap<>();

    private ConcurrentHashMap<Integer, HostInfo> playersIds = new ConcurrentHashMap<>();

    private NumSequenceGenerator numSequenceGenerator = new NumSequenceGenerator();

    private ConcurrentHashMap<HostInfo, SnakesProto.NodeRole> allPlayers = new ConcurrentHashMap<>();

    private HostInfo master = null;

    private int masterId = -1;

    private HostInfo deputy = null;

    private int deputyId = -1;

    private int myId;

    private int pingDelay;
    private int nodeTimeout;

    private boolean becomingViewer = false;
    private boolean wantToExit = false;

    private Timer sender;
    private Timer nodesTimeoutChecker;
    private Thread receiver;

    private SnakesProto.NodeRole nodeRole;

    private ConcurrentHashMap<HostInfo, ConcurrentHashMap<Long, Long>> lastIds = new ConcurrentHashMap<>();

    public MessageManager(SnakeGame _snakeGame, SnakesProto.GameMessage.AnnouncementMsg announcementMsg,
                          HostInfo master, SnakesProto.NodeRole _nodeRole)
    {
        nodeRole = _nodeRole;

        snakeGame = _snakeGame;

        gameWindow = snakeGame.getGameWindow();

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

        snakeGame = _snakeGame;

        //System.out.println("MESS MANAGER CTOR WITH: " + (_snakeGame == null));

        gameWindow = snakeGame.getGameWindow();

        Init(gameConfig);

    }

    private void Init(SnakesProto.GameConfig gameConfig)
    {
        pingDelay = gameConfig.getPingDelayMs();
        nodeTimeout = gameConfig.getNodeTimeoutMs();

        try
        {
            socket = new DatagramSocket();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        System.out.println("ME: " + socket.getLocalAddress().getHostAddress() + " : " + socket.getLocalPort());

        receiver = new Thread(() ->
        {

            try
            {
                socket.setSoTimeout(pingDelay);
            }
            catch (SocketException e)
            {
                e.printStackTrace();
            }

            while(!Thread.interrupted())
            {
                try
                {
                    DatagramPacket dp = new DatagramPacket(buffer, 8192);
                    socket.receive(dp);
                    proccessedMessage(dp);

                }
                catch(SocketTimeoutException ignored) {}
                catch (IOException e)
                {
                    System.out.println("Receiver interrupt error: " + e.getMessage());
//                    e.printStackTrace();
                }
            }
        });

        receiver.start();

        sender = new Timer();
        sender.scheduleAtFixedRate(
        new TimerTask()
        {
            @Override
            public void run()
            {
                System.out.println("MASTER: " + (master == null ? "null" : master.getIp() + ":" + master.getPort() + "; id: " + masterId ));
                System.out.println("DEPUTY: " + (deputy == null ? "null" : deputy.getIp() + ":" + deputy.getPort() + "; id: " + deputyId ));
                if(nodeRole == SnakesProto.NodeRole.MASTER)
                {
                    for(var entry : playersIds.entrySet())
                    {
                        if(entry.getKey() == myId) continue;

                        if(!messages.containsKey(entry.getValue()))
                            messages.put(entry.getValue(), new ConcurrentHashMap<>());
                        if(messages.get(entry.getValue()).size() == 0)
                        {
                            SnakesProto.GameMessage newPing = createPing();
                            messages.get(entry.getValue()).put(newPing.getMsgSeq(), newPing);
                        }
                    }
                }
                else
                {
                    if(master == null) { System.out.println("WTF?"); return; }
                    if(!messages.containsKey(master))
                        messages.put(master, new ConcurrentHashMap<>());
                    if(messages.get(master).size() == 0)
                    {
                        SnakesProto.GameMessage newPing = createPing();
                        messages.get(master).put(newPing.getMsgSeq(), newPing);
                    }
                }

                System.out.println("MY ROLE:" + nodeRole);

                System.out.println("_________________MESSAGES TO SEND_________________" + messages.size());
                //System.out.println("Total messages to send: " + messages.size());
                for(Map.Entry<HostInfo, ConcurrentHashMap<Long, SnakesProto.GameMessage>> firstEntry : messages.entrySet())
                {
                    System.out.println("MESSAGES FOR: " + firstEntry.getKey().getIp() + " ; " + firstEntry.getKey().getPort() + ": num = " + firstEntry.getValue().size());
                    for(Map.Entry<Long, SnakesProto.GameMessage> secondEntry : firstEntry.getValue().entrySet())
                    {
                        System.out.println("\t" + messageToString(secondEntry.getValue()) + "\n");
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

                //System.out.println("Message to send ");

            }
        }, 0, pingDelay);

        nodesTimeoutChecker = new Timer();
        nodesTimeoutChecker.scheduleAtFixedRate(new TimerTask() {

            private ArrayList<HostInfo> timeoutedHosts = new ArrayList<>();

            @Override
            public void run()
            {

                System.out.println("____________________________GAME PLAYERS___________________________");

                for(Map.Entry<Integer, SnakesProto.GamePlayer> gp : snakeGame.getPlayers().entrySet())
                {
                    System.out.println("id: " + gp.getKey() + "; name:" + gp.getValue().getName() + "; role: " + gp.getValue().getRole() + "; port: " + gp.getValue().getPort());
                }
                System.out.println("____________________________GAME PLAYERS___________________________");
                System.out.println("____________________________HOSTS__________________________________");
                System.out.println("ME: port: " + getMyPort() + "; " + nodeRole);

                for(Map.Entry<HostInfo, SnakesProto.NodeRole> entry : allPlayers.entrySet())
                {
                    System.out.println("port: " + entry.getKey().getPort() + "; role: " + entry.getValue());
                }
                System.out.println("____________________________HOSTS__________________________________");


                boolean hasDeputy = false;
                long timeNow = System.currentTimeMillis();
                for(Map.Entry<HostInfo, Long> entry: nodesTimeout.entrySet())
                {
                    System.out.println("TIMEOUT: " + entry.getKey().getIp().getHostAddress() + ":" + entry.getKey().getPort() + ":" + allPlayers.get(entry.getKey()) + " : " + (timeNow - entry.getValue()));
                    if(allPlayers.get(entry.getKey()) == SnakesProto.NodeRole.DEPUTY)
                    {
                        hasDeputy = true;
                    }
                    if(timeNow - entry.getValue() > nodeTimeout)
                    {
                        timeoutedHosts.add(entry.getKey());
                    }
                }

                boolean masterDeadFlag = false;
                boolean deputyDeadFlag = false;


                for(HostInfo hi : timeoutedHosts)
                {

                    SnakesProto.NodeRole killedNodeRole = allPlayers.get(hi);

                    System.out.println("Host timeouted: " + hi.getIp() + " : " + hi.getPort() + "; Role: " + killedNodeRole);


                    if(nodeRole == SnakesProto.NodeRole.MASTER)
                    {
                        int hiId = findPlayerIdByHostInfo(hi);
                        if(hiId != -1)
                        {
                            var snakes = snakeGame.getSnakes();
                            if(snakes.containsKey(hiId))
                            {
                                snakes.get(hiId).setSnakeState(SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
                            }
                        }

                        snakeGame.getPlayers().remove(hiId);
                    }

                    allPlayers.remove(hi);
                    lastIds.remove(hi);
                    nodesTimeout.remove(hi);

                    if(killedNodeRole != SnakesProto.NodeRole.MASTER)
                    {
                        messages.remove(hi);
                        //System.out.println("REMOVE ALL MESSAGES FROM THIS CLIENT(" + messages.get(hi).size() +  "): " + hi.getPort());
                    }
                    else
                    {
                        masterDeadFlag = true;
                    }

                    if(killedNodeRole == SnakesProto.NodeRole.DEPUTY)
                    {
                        deputyDeadFlag = true;
                    }
                }

                HostInfo prevMaster = master;

                System.out.println("Master dead: " + masterDeadFlag + "; deputy dead: " + deputyDeadFlag);


                if(masterDeadFlag)
                {
                    System.out.println("My node role: " + nodeRole);
                    if(nodeRole == SnakesProto.NodeRole.VIEWER && !hasDeputy)
                    {
                        gameWindow.terminate();
                    }
                    else if(nodeRole == SnakesProto.NodeRole.DEPUTY)
                    {
                        System.out.println("Before became Master");
                        becameMaster();
                    }
                    else if(!deputyDeadFlag)
                    {
                        findNewMaster();
                    }

                    if(master == null)
                    {
                        for (Map.Entry<Long, SnakesProto.GameMessage> entry : messages.get(prevMaster).entrySet())
                        {

                            if (entry.getValue().hasSteer())
                                snakeGame.changeSnakeDir(myId, entry.getValue().getSteer().getDirection());
                        }
                    }
                    else
                    {
                        messages.put(master, messages.get(prevMaster));
                    }

                    messages.remove(prevMaster);
                }

                if(nodeRole == SnakesProto.NodeRole.MASTER && deputyDeadFlag)
                {
                    changeDeputy();
                }


                timeoutedHosts.clear();

                final long timeNow2 = System.currentTimeMillis();
                for(var a : lastIds.entrySet())
                {
                    a.getValue().entrySet().removeIf(b -> (b.getValue() - timeNow2) > nodeTimeout);
                }

            }
        }, 0, nodeTimeout);

    }

    private void proccessedMessage(DatagramPacket dp)
    {
        try
        {
//            byte messBytes[] = new byte[dp.getLength()];
//
//            System.arraycopy(dp.getData(), 0, messBytes, 0, dp.getLength());

            SnakesProto.GameMessage mess =
                    SnakesProto.GameMessage.parseFrom(ByteBuffer.wrap(dp.getData(), 0, dp.getLength()));

            System.out.println("RECEIVED FROM: " + dp.getPort() + "; " + messageToString(mess));

            HostInfo sender = new HostInfo(dp.getAddress(), dp.getPort());

            if(mess.hasPing())
            {
                //System.out.println("RECEIVED PING");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());
                }

                sendAck(mess, sender);

            }
            else if(mess.hasSteer())
            {
                //System.out.println("RECEIVED STEER");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    snakeGame.changeSnakeDir(mess.getSenderId(), mess.getSteer().getDirection());
                }

                sendAck(mess, sender);

            }
            else if(mess.hasAck())
            {
                //System.out.println("RECEIVED ACK");
                //System.out.println(mess.getMsgSeq());

                //if(lastIds.contains(mess.getMsgSeq())) return;

                if(!messages.containsKey(sender)) return;

                if(!messages.get(sender).containsKey(mess.getMsgSeq())) return;

                nodesTimeout.put(sender, System.currentTimeMillis());

                SnakesProto.GameMessage messThatAcked = messages.get(sender).get(mess.getMsgSeq());

                if(messThatAcked.hasJoin())
                {
                    System.out.println("Join Acked");
                    master = sender;
                    allPlayers.put(sender, SnakesProto.NodeRole.MASTER);
                    playersIds.put(mess.getSenderId(), master);
                    snakeGame.getGameWindow().setPi(mess.getReceiverId());

//                    messages.get(sender).remove(mess.getMsgSeq());

                    SnakesProto.GameMessage newPingMsg = createPing();

                    sendAndStoreMessage(master, newPingMsg);
                    masterId = mess.getSenderId();

                    myId = mess.getReceiverId();

                }
                else if(messThatAcked.hasRoleChange())
                {
                    System.out.println(mess);
                    System.out.println(messThatAcked);
                    SnakesProto.GameMessage.RoleChangeMsg rlChgMsg = messThatAcked.getRoleChange();

                    System.out.println("Becoming viewer flag: " + becomingViewer);

                    if(becomingViewer && messThatAcked.getSenderId() == myId
                            && rlChgMsg.hasSenderRole()
                            && rlChgMsg.getSenderRole() == SnakesProto.NodeRole.VIEWER)
                    {


                        becomingViewer = false;

                        System.out.println("Want to exit in ack: " + wantToExit);

                        if(wantToExit)
                        {
                            gameWindow.terminate();
                        }
//                        else
//                        {
//                            allPlayers.clear();
//                            allPlayers.put(sender, SnakesProto.NodeRole.MASTER);
//                        }
                    }
                    //SnakesProto.GameMessage newPing = createPing();
//                    sendAndStoreMessage(sender, newPing);
                    //messages.get(sender).put(newPing.getMsgSeq(), newPing);
                }

                messages.get(sender).remove(mess.getMsgSeq());

            }
            else if(mess.hasState())
            {
                //SnakesProto.GameState gameState1 = mess.getState().getState();
                //System.out.println("RECEIVED STATE: " + gameState1.getStateOrder() + "\n" + gameState1);
                if(nodeRole == SnakesProto.NodeRole.MASTER)
                {
                    sendAck(mess, sender);
                    return;
                }
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    SnakesProto.GameState gameState = mess.getState().getState();
                    System.out.println(gameState);
                    if (gameState.getStateOrder() < snakeGame.getGameStateCounter())
                    {
                        sendAck(mess, sender);
                        return;
                    }
                    snakeGame.loadState(gameState, sender);
                    snakeGame.getGameWindow().repaint();


//                  List<SnakesProto.GamePlayer> players = gameState.getPlayers().getPlayersList();
//                  for(Map.Entry<Integer, HostInfo>)

                    Map<Integer, Boolean> hasPlayer = new HashMap<>();

                    for (SnakesProto.GamePlayer player : gameState.getPlayers().getPlayersList())
                    {
                        if (player.getId() == myId)
                        {
                            continue;
                        }

                        HostInfo hi;
                        if (!playersIds.containsKey(player.getId()))
                        {
                            if (!player.getIpAddress().equals(""))
                            {
                                try
                                {
                                    hi = new HostInfo(InetAddress.getByName(player.getIpAddress()), player.getPort());
                                }
                                catch (UnknownHostException e)
                                {
                                    System.out.println("Cannot decode ip from: " + player.getIpAddress());
                                    continue;
                                }
                            }
                            else
                            {
                                hi = sender;
                            }
                        }
                        else
                        {
                            hi = playersIds.get(player.getId());
                        }

                        System.out.println("-----PLAYER_ID: " + player.getId() + " " + player.getIpAddress() + ":" + player.getPort() + " " + player.getName() + " " + player.getRole());
                        playersIds.put(player.getId(), hi);
                        allPlayers.put(hi, player.getRole());
                        if(player.getRole() == SnakesProto.NodeRole.DEPUTY)
                        {
                            deputyId = player.getId();
                            deputy = hi;
                        }
                        else if(player.getRole() == SnakesProto.NodeRole.MASTER)
                        {
                            masterId = player.getId();
                            master = hi;
                        }


                        hasPlayer.put(player.getId(), true);
                    }

                    Iterator<Map.Entry<Integer, HostInfo>> it = playersIds.entrySet().iterator();
                    while (it.hasNext())
                    {
                        Map.Entry<Integer, HostInfo> entry = it.next();
                        if (hasPlayer.containsKey(entry.getKey()) && !hasPlayer.get(entry.getKey()))
                        {
                            playersIds.remove(entry.getKey());
                            allPlayers.remove(entry.getValue());
                            messages.remove(entry.getValue());
                            nodesTimeout.remove(entry.getValue());
                        }
                    }

                    boolean meDead = snakeGame.isDead(myId);

                    if (meDead && (nodeRole != SnakesProto.NodeRole.VIEWER))
                    {
                        SnakesProto.GameMessage.RoleChangeMsg.Builder roleChangeMsg =
                                SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                        .setReceiverRole(SnakesProto.NodeRole.MASTER)
                                        .setSenderRole(SnakesProto.NodeRole.VIEWER);

                        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                                .setRoleChange(roleChangeMsg)
                                .setSenderId(myId)
                                .setReceiverId(masterId)
                                .setMsgSeq(numSequenceGenerator.getNextNum())
                                .build();

                        sendAndStoreMessage(master, message);
                    }

                }

                sendAck(mess, sender);

            }
            else if(mess.hasJoin())
            {
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    SnakesProto.GameMessage.JoinMsg joinMsg = mess.getJoin();


                    SnakesProto.PlayerType newPlayerType = SnakesProto.PlayerType.HUMAN;
                    if(joinMsg.hasPlayerType())
                    {
                        newPlayerType = joinMsg.getPlayerType();
                    }


                    SnakesProto.NodeRole newNodeRole = SnakesProto.NodeRole.VIEWER;
                    if(!joinMsg.hasOnlyView() || !joinMsg.getOnlyView())
                    {
                        newNodeRole = SnakesProto.NodeRole.NORMAL;
                    }

                    int newPlayerId = snakeGame.addPlayer(mess.getJoin().getName(),
                            newNodeRole, newPlayerType, sender.getIp().getHostAddress(), sender.getPort());

                    if(messages.get(sender) == null)
                    {
                        messages.put(sender, new ConcurrentHashMap<>());
                    }

                    if(newPlayerId == -1)
                    {
                        SnakesProto.GameMessage errorMes = SnakesProto.GameMessage.newBuilder()
                                .setError(SnakesProto.GameMessage.ErrorMsg.newBuilder()
                                        .setErrorMessage("No place for you"))
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

                        allPlayers.put(sender, newNodeRole);

                        playersIds.put(newPlayerId, sender);

                        sendAck(mess, sender);

//                        sendMessage(sender, ack);

                        if(deputy == null)
                        {
                            deputy = sender;
                            deputyId = newPlayerId;

                            SnakesProto.GameMessage roleChangeMess = SnakesProto.GameMessage.newBuilder()
                                    .setMsgSeq(numSequenceGenerator.getNextNum())
                                    .setReceiverId(newPlayerId)
                                    .setSenderId(myId)
                                    .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                            .setSenderRole(SnakesProto.NodeRole.MASTER)
                                            .setReceiverRole(SnakesProto.NodeRole.DEPUTY))
                                    .build();

                            allPlayers.put(sender, SnakesProto.NodeRole.DEPUTY);
                            snakeGame.getPlayers().put(newPlayerId,
                                    snakeGame.getPlayers().get(newPlayerId).toBuilder().
                                            setRole(SnakesProto.NodeRole.DEPUTY).build());

                            sendAndStoreMessage(sender, roleChangeMess);
                        }
                    }
                }
                else if(allPlayers.containsKey(sender))
                {
                    sendAck(mess, sender);
                }

            }
            else if(mess.hasError())
            {
                //System.out.println("RECEIVED ERROR");
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {
                    if (!lastIds.containsKey(sender))
                    {
                        lastIds.put(sender, new ConcurrentHashMap<>());
                    }

                    lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    ErrorBox.display(mess.getError().getErrorMessage());

                    gameWindow.terminate();
                }

                sendAck(mess, sender);
            }
            else if(mess.hasRoleChange())
            {
                System.out.println("RECEIVED ROLE CHANGE");
                System.out.println(mess);
                if(!lastIds.containsKey(sender) || !lastIds.get(sender).contains(mess.getMsgSeq()))
                {

                    //System.out.println("I'm in askndaknld");
                    if(mess.hasReceiverId() && mess.hasSenderId())
                    {

                        //System.out.println("Jimba1");
                        SnakesProto.GameMessage.RoleChangeMsg roleChangeMsg = mess.getRoleChange();

                        var players = snakeGame.getPlayers();

                        if(roleChangeMsg.hasReceiverRole() && players.containsKey(mess.getReceiverId()))
                        {
                            //System.out.println("Jimba1/1");
                            HostInfo messReceiver = playersIds.get(mess.getReceiverId());
                            if(messReceiver != null)
                            {
                                allPlayers.put(messReceiver, roleChangeMsg.getReceiverRole());
                            }

                            if(mess.getReceiverId() == myId) changeRole(roleChangeMsg.getReceiverRole());

                            if(nodeRole == SnakesProto.NodeRole.MASTER)
                            {
                                players.put(mess.getReceiverId(), players.get(mess.getReceiverId()).toBuilder().setRole(roleChangeMsg.getReceiverRole()).build());
                                changeDeputy();
                            }

                            //System.out.println("My Id: " + myId + " ; ReceiverId: " + mess.getReceiverId());
                        }
                        else
                        {
                            System.out.println("I'm here and i'm don't sen ack why?");
                            return;
                        }

                        //System.out.println("Jimba2");
                        if(roleChangeMsg.hasSenderRole() && players.containsKey(mess.getSenderId()))
                        {
                            //System.out.println("Jimba2/1");
                            HostInfo messSender = playersIds.get(mess.getSenderId());
                            if(messSender != null)
                            {
                                allPlayers.put(messSender, roleChangeMsg.getSenderRole());
                            }

                            if(nodeRole == SnakesProto.NodeRole.MASTER)
                            {
                                players.put(mess.getSenderId(), players.get(mess.getSenderId()).toBuilder().setRole(roleChangeMsg.getSenderRole()).build());
                                if(roleChangeMsg.getSenderRole() == SnakesProto.NodeRole.VIEWER)
                                {
                                    changeDeputy();
                                }
                            }

                            if(roleChangeMsg.getSenderRole() == SnakesProto.NodeRole.MASTER)
                            {
                                master = messSender;
                            }
                            if(roleChangeMsg.getSenderRole() == SnakesProto.NodeRole.DEPUTY)
                            {
                                deputy = messSender;
                            }
                        }


                        //System.out.println("Jimba3");
                        if (!lastIds.containsKey(sender))
                        {
                            lastIds.put(sender, new ConcurrentHashMap<>());
                        }

                        lastIds.get(sender).put(mess.getMsgSeq(), System.currentTimeMillis());

                    }
                    else
                    {
                        System.out.println("Incorrect role change request");
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

    private SnakesProto.GameMessage createAck(SnakesProto.GameMessage gameMessage, int receiverId)
    {
        return SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(gameMessage.getMsgSeq())
                .setAck(SnakesProto.GameMessage.AckMsg.newBuilder().build())
                .setSenderId(myId)
                .setReceiverId(receiverId)
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

    public void disableMessageManager()
    {
        receiver.interrupt();
        nodesTimeoutChecker.cancel();
        sender.cancel();
        socket.close();
    }

    public void sendState()
    {
        List<Integer> killedSnakes = snakeGame.getDeadSnakes();

        boolean masterDead = false;
        boolean deputyDead = false;

        List<Integer> hostsForViewerChange = new ArrayList<>();

        for(int id : killedSnakes)
        {
            if(id == myId)
            {
                masterDead = true;
            }
            else if(deputy != null && id == findPlayerIdByHostInfo(deputy))
            {
                deputyDead = true;
            }
            else
            {
                HostInfo hi = playersIds.get(id);
                SnakesProto.NodeRole nr = allPlayers.get(hi);
            }
        }

        if(masterDead)
        {
            System.out.println("YA SDOH X(");
            becameViewer();
        }
        else
        {
            if(deputyDead)
            {
                changeDeputy();
            }
        }



        SnakesProto.GameState gameState = snakeGame.generateNewState();

        //System.out.println("GENERATED STATE:\n" + gameState.getSnakes(0));

        SnakesProto.GameMessage.StateMsg.Builder stateMsgBuilder = SnakesProto.GameMessage.StateMsg.newBuilder()
                .setState(gameState);



//
//        for(Map.Entry<Integer, HostInfo> entry : playersIds.entrySet())
//        {
//            if(!snakes.containsKey(entry.getKey()) &&
//                                                    allPlayers.get(entry.getValue()) != SnakesProto.NodeRole.VIEWER)
//            {
//                SnakesProto.GameMessage roleChgMsg = SnakesProto.GameMessage.newBuilder()
//                                .build();
//            }
//        }




        for(Map.Entry<Integer, HostInfo> entry : playersIds.entrySet())
        {
            if(entry.getKey() == myId) continue;

            SnakesProto.GameMessage stateMsg = SnakesProto.GameMessage.newBuilder()
                    .setState(stateMsgBuilder)
                    .setMsgSeq(numSequenceGenerator.getNextNum())
                    .build();



            sendAndStoreMessage(entry.getValue(), stateMsg);

//            messages.get(hi).put(stateMsg.getMsgSeq(), stateMsg);
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

        myId = newId;

        return newId;
    }

    private void sendAck(SnakesProto.GameMessage gameMessage, HostInfo hi)
    {
        //System.out.println("Send Ack to:" + hi.getIp().getHostAddress() + ":" + hi.getPort());
        int receiverId = findPlayerIdByHostInfo(hi);
        if(receiverId == -1)
        {
            System.out.println("Can't find playerId by host: " + hi.getIp().getHostAddress() + ":" + hi.getPort());
            return;
        }
        SnakesProto.GameMessage ack = createAck(gameMessage, receiverId);

        sendMessage(hi, ack);
    }

    private SnakesProto.GameMessage createPing()
    {
        SnakesProto.GameMessage pingMsg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(numSequenceGenerator.getNextNum())
                .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                .build();

        return pingMsg;
    }

    private void sendMessage(HostInfo receiver, SnakesProto.GameMessage message)
    {
        byte[] messByte = message.toByteArray();
        DatagramPacket ackDp = new DatagramPacket(messByte, 0, messByte.length,
                receiver.getIp(), receiver.getPort());

        try
        {
            if(!socket.isClosed())
                socket.send(ackDp);
            System.out.println("SEND TO: " + receiver.getPort() + "; MESSAGE:" + messageToString(message));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void sendAndStoreMessage(HostInfo receiver, SnakesProto.GameMessage message)
    {
        if(!messages.containsKey(receiver))
            messages.put(receiver, new ConcurrentHashMap<>());

        sendMessage(receiver, message);

        messages.get(receiver).put(message.getMsgSeq(), message);
    }

    private String messageToString(SnakesProto.GameMessage mess)
    {
        String result = "Message: Seq: " + mess.getMsgSeq();
        if(mess.hasJoin()) return result + "; TYPE: Join";
        if(mess.hasAnnouncement()) return result + "; TYPE: Annon";
        if(mess.hasRoleChange()) return result + "; TYPE: RoleChange";
        if(mess.hasError()) return result + "; TYPE: Error";
        if(mess.hasState()) return result + "; TYPE: State";
        if(mess.hasAck()) return result + "; TYPE: Ack";
        if(mess.hasSteer()) return result + "; TYPE: Steer";
        if(mess.hasPing()) return result + "; TYPE: Ping";

        return result + "; TYPE: UNKNOWN";
    }

    private void changeDeputy()
    {
        if(allPlayers.size() == 0) return;

        deputy = null;
        deputyId = -1;

        for(Map.Entry<HostInfo, SnakesProto.NodeRole> entry : allPlayers.entrySet())
        {
            if(entry.getValue() == SnakesProto.NodeRole.DEPUTY)
            {
                deputy = entry.getKey();
                deputyId = findPlayerIdByHostInfo(entry.getKey());
                return;
            }
            if(entry.getValue() == SnakesProto.NodeRole.NORMAL)
            {
                int receiverId = findPlayerIdByHostInfo(entry.getKey());
                if(receiverId == -1)
                {
                    System.out.println("Cannot find receiver id");
                    continue;
                }

                SnakesProto.GameMessage roleChangeMsg = createRoleChangeMessage(receiverId, myId,
                                                SnakesProto.NodeRole.DEPUTY, SnakesProto.NodeRole.MASTER);

                HashMap<Integer, SnakesProto.GamePlayer> players = snakeGame.getPlayers();
                players.put(receiverId, players.get(receiverId).toBuilder().setRole(SnakesProto.NodeRole.DEPUTY).build());
                allPlayers.put(entry.getKey(), SnakesProto.NodeRole.DEPUTY);
                sendAndStoreMessage(entry.getKey(), roleChangeMsg);

//                messages.get(entry.getKey()).put(roleChangeMsg.getMsgSeq(), roleChangeMsg);
                break;
            }
        }
    }

    private SnakesProto.GameMessage createRoleChangeMessage(int receiverId, int senderId,
                                    SnakesProto.NodeRole receiverRole, SnakesProto.NodeRole senderRole)
    {

        return SnakesProto.GameMessage.newBuilder()
                .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                                    .setReceiverRole(receiverRole)
                                    .setSenderRole(senderRole))
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setMsgSeq(numSequenceGenerator.getNextNum())
                .build();
    }


    private int findPlayerIdByHostInfo(HostInfo hostInfo)
    {
        int playerId = -1;
        for (Map.Entry<Integer, HostInfo> entry : playersIds.entrySet())
        {
            if (entry.getValue().equals(hostInfo))
            {
                playerId = entry.getKey();
                break;
            }
        }

        return playerId;
    }


    public void safeExit()
    {
        if(nodeRole == SnakesProto.NodeRole.VIEWER)
            gameWindow.terminate();

        wantToExit = true;
        becameViewer();
    }

    public void becameViewer()
    {
        if(nodeRole == SnakesProto.NodeRole.VIEWER) return;

        SnakesProto.GameMessage.Builder roleChangeMsg = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(numSequenceGenerator.getNextNum())
                .setSenderId(myId);

        SnakesProto.GameMessage.RoleChangeMsg.Builder rlChgMsgBuilder
                = SnakesProto.GameMessage.RoleChangeMsg.newBuilder();

        rlChgMsgBuilder.setSenderRole(SnakesProto.NodeRole.VIEWER);
        rlChgMsgBuilder.setReceiverRole(SnakesProto.NodeRole.MASTER);

        roleChangeMsg.setRoleChange(rlChgMsgBuilder);


        becomingViewer = true;

        if(nodeRole == SnakesProto.NodeRole.MASTER)
        {
            System.out.println("DEPUTY is null?: " + (deputy == null));
            if(deputy != null)
            {
                roleChangeMsg.setReceiverId(deputyId);
                sendAndStoreMessage(deputy, roleChangeMsg.build());
            }
            else
            {
                gameWindow.terminate();
            }
        }
        else
        {
            roleChangeMsg.setReceiverId(masterId);
            sendAndStoreMessage(master, roleChangeMsg.build());
        }

        changeRole(SnakesProto.NodeRole.VIEWER);

    }

    private void becameMaster()
    {
        changeRole(SnakesProto.NodeRole.MASTER);

        master = null;
        masterId = -1;

        SnakesProto.GameMessage.Builder gameMessageBuilder = SnakesProto.GameMessage.newBuilder();

        SnakesProto.GameMessage.RoleChangeMsg.Builder roleChgMsgBuilder = SnakesProto.GameMessage.RoleChangeMsg.newBuilder();

        roleChgMsgBuilder.setSenderRole(SnakesProto.NodeRole.MASTER);

        gameMessageBuilder.setSenderId(myId);

        for(Map.Entry<Integer, HostInfo> entry : playersIds.entrySet())
        {

            gameMessageBuilder.setReceiverId(entry.getKey());

            if(!allPlayers.containsKey(entry.getValue())) continue;

            roleChgMsgBuilder.setReceiverRole(allPlayers.get(entry.getValue()));

            if(deputy == null && allPlayers.get(entry.getValue()) != SnakesProto.NodeRole.VIEWER)
            {
                deputy = entry.getValue();
                deputyId = entry.getKey();
                roleChgMsgBuilder.setReceiverRole(SnakesProto.NodeRole.DEPUTY);
            }

            gameMessageBuilder.setMsgSeq(numSequenceGenerator.getNextNum());

            SnakesProto.GameMessage message = gameMessageBuilder.setRoleChange(roleChgMsgBuilder.build()).build();

            sendMessage(entry.getValue(), message);

        }

    }

    private void findNewMaster()
    {
        for(var entry : playersIds.entrySet())
        {
            System.out.println("Players: " + entry.getKey() + " ; " + entry.getValue());
            if(allPlayers.get(entry.getValue()) == SnakesProto.NodeRole.DEPUTY)
            {
                master = entry.getValue();
                masterId = entry.getKey();
                if(!nodesTimeout.containsKey(master))
                {
                    nodesTimeout.put(master, System.currentTimeMillis());
                }
            }
        }
    }

    private void changeRole(SnakesProto.NodeRole _nodeRole)
    {
        System.out.println("PrevRole: " + nodeRole + "; NewRole: " + _nodeRole);
        if(nodeRole != _nodeRole)
        {
            if(nodeRole == SnakesProto.NodeRole.VIEWER)
            {
                return;
            }

            if(nodeRole == SnakesProto.NodeRole.MASTER)
            {
                master = deputy;
                masterId = deputyId;
                deputy = null;
                deputyId = -1;
            }

            nodeRole = _nodeRole;
            //System.out.println("Game window null&: " + (gameWindow == null));
            gameWindow.setNodeRole(nodeRole);
        }
    }

}
