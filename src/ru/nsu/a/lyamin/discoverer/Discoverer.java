package ru.nsu.a.lyamin.discoverer;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.nsu.a.lyamin.PlayerInfo;
import ru.nsu.a.lyamin.message_decoder.SnakesProto;
import ru.nsu.a.lyamin.message_manager.HostInfo;
import ru.nsu.a.lyamin.message_manager.MessageManager;
import ru.nsu.a.lyamin.snake_game.Snake;
import ru.nsu.a.lyamin.snake_game.SnakeGame;
//import ru.nsu.a.lyamin.view.SessionInfo;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class Discoverer implements Runnable
{
    private byte[] buffer = new byte[8192];
    private MulticastSocket socket;
    private boolean hasToSend = false;

    private SnakeGame snakeGame;
    private SnakesProto.GameConfig gameConfig;

    private int timeout = 5000;

    private InetAddress groupIp;
    private int groupPort;

    private Timer timer = new Timer();

    private HashMap<HostInfo, Long> lastUpdate;

    private ConcurrentHashMap<HostInfo, SnakesProto.GameMessage.AnnouncementMsg> sessionInfoMap;

    public Discoverer(ConcurrentHashMap<HostInfo, SnakesProto.GameMessage.AnnouncementMsg> _sessionInfoMap) throws IOException
    {
        lastUpdate = new HashMap<>();
        sessionInfoMap = _sessionInfoMap;
        socket = new MulticastSocket(9192);

        groupIp = InetAddress.getByName("239.192.0.4");
        groupPort = 9192;
        socket.joinGroup(groupIp);
    }


    @Override
    public void run()
    {
        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

        try
        {
            socket.setSoTimeout(timeout);
        }
        catch (SocketException e)
        {
            e.printStackTrace();
        }

        while(!Thread.interrupted())
        {

            try
            {
                socket.receive(dp);

                proccessedMessage(dp);

            }
            catch(SocketTimeoutException ignored){}
            catch (IOException e)
            {
                e.printStackTrace();
                return;
            }

            checkMap();
        }

        socket.close();
        timer.cancel();
    }

    private void checkMap()
    {
        long timeNow = System.currentTimeMillis();
        lastUpdate.entrySet().removeIf( entry -> timeNow - entry.getValue() > timeout);
        sessionInfoMap.entrySet().removeIf( entry -> !lastUpdate.containsKey(entry.getKey()));
    }

    private SnakesProto.GameMessage getMessage()
    {
        SnakesProto.GameMessage.Builder gameMessageBuilder = SnakesProto.GameMessage.newBuilder();

        SnakesProto.GameMessage.AnnouncementMsg.Builder annonMesBuilder = SnakesProto.GameMessage.AnnouncementMsg.newBuilder();

        annonMesBuilder.setConfig(gameConfig);

        SnakesProto.GamePlayers.Builder gamePlayersBuilder = SnakesProto.GamePlayers.newBuilder();

        MessageManager messageManager = snakeGame.getMessageManager();


        for(Map.Entry<Integer, SnakesProto.GamePlayer> entry : snakeGame.getPlayers().entrySet())
        {
            //System.out.println("PLAYER IN GET MESSAGE");
            gamePlayersBuilder.addPlayers(entry.getValue());
        }

        annonMesBuilder.setPlayers(gamePlayersBuilder);

        gameMessageBuilder.setAnnouncement(annonMesBuilder);

        gameMessageBuilder.setMsgSeq(1);

        return gameMessageBuilder.build();
    }

    private void proccessedMessage(DatagramPacket dp)
    {
        try
        {
            byte messBytes[] = new byte[dp.getLength()];

            System.arraycopy(dp.getData(), 0, messBytes, 0, dp.getLength());

            SnakesProto.GameMessage snakesProto = SnakesProto.GameMessage.parseFrom(messBytes);
            if(!snakesProto.hasAnnouncement()) { return; }

            SnakesProto.GameMessage.AnnouncementMsg announcementMsg = snakesProto.getAnnouncement();

            HostInfo hi = null;

            for(SnakesProto.GamePlayer player : announcementMsg.getPlayers().getPlayersList())
            {
                if(player.getRole() == SnakesProto.NodeRole.MASTER)
                {
                    hi = new HostInfo(dp.getAddress(), player.getPort());
                }
            }

            if(hi == null) return;

            sessionInfoMap.put(hi, announcementMsg);

            lastUpdate.put(hi, System.currentTimeMillis());

        }
        catch (InvalidProtocolBufferException e)
        {
            e.printStackTrace();
        }
    }

    public void sendAnnouncementMsg(SnakeGame _snakeGame, SnakesProto.GameConfig _gameConfig)
    {
        snakeGame = _snakeGame;
        gameConfig = _gameConfig;

        hasToSend = true;

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run()
            {
                //System.out.println("Has to send:" + hasToSend);
                if(!hasToSend) return;
                //System.out.println("Message:"  + getMessage());
                SnakesProto.GameMessage message = getMessage();
                byte [] messageByte = message.toByteArray();
                try
                {
                    socket.send(new DatagramPacket(messageByte, messageByte.length, groupIp, groupPort));
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

            }
        }, 0, 1000);
    }

    public void stopSendAnnouncementMsg()
    {
        hasToSend = false;
        timer.cancel();
    }
}
