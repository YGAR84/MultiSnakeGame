package ru.nsu.a.lyamin;

import java.net.InetAddress;

public class PlayerInfo
{
    private InetAddress ip;
    private int port;
    private String name;

    public PlayerInfo(InetAddress _ip, int _port, String _name)
    {
        ip = _ip;
        port = _port;
        name = _name;
    }

    @Override
    public int hashCode()
    {
        return (ip.toString() + port + name).hashCode();
    }

    public String getName()
    {
        return name;
    }

    @Override
    public boolean equals(Object obj)
    {

        if(this == obj)
            return true;

        if(obj == null)
            return false;

        if(getClass() != obj.getClass())
            return false;

        PlayerInfo pi = (PlayerInfo)obj;

        return pi.ip.equals(ip) && pi.name.equals(name) && pi.port == port;
    }

    public int getPort()
    {
        return port;
    }

    public InetAddress getIp()
    {
        return ip;
    }
}
