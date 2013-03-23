package panda.core.containers;

import java.nio.channels.DatagramChannel;

import panda.core.IAction;


public class MulticastRegistration implements IAction
{
	private final DatagramChannel channel;
	private final String ip;
	private final int port;
	private final Object attachment;
	
	public MulticastRegistration(DatagramChannel channel, String ip, int port, Object attachment)
	{
		this.channel = channel;
		this.ip = ip;
		this.port = port;
		this.attachment = attachment;
	}

	public DatagramChannel getChannel()
	{
		return this.channel;
	}

	public String getIp()
	{
		return this.ip;
	}

	public int getPort()
	{
		return this.port;
	}

	public Object getAttachment()
	{
		return this.attachment;
	}

	@Override
	public int getAction()
	{
		return IAction.REGISTER_MULTICAST_READ;
	}
}