package panda.core;

import java.util.HashMap;
import java.util.Map;

import panda.core.containers.TopicInfo;


public class Receiver
{
	private final SelectorThread selectorThread;
	private final int bindPort;
	private final Map<String, ChannelReceiveInfo> channelInfos;

	public Receiver(SelectorThread selectorThread, int bindPort)
	{
		this.selectorThread = selectorThread;
		this.bindPort = bindPort;
		this.channelInfos = new HashMap<String, ChannelReceiveInfo>();
	}

	public void subscribe(TopicInfo topicInfo, String interfaceIp, IDataListener listener)
	{
		ChannelReceiveInfo receiveInfo = getChannelReceiverInfo(topicInfo.getIp(), topicInfo.getPort().intValue(), topicInfo.getMulticastGroup(), interfaceIp);
		synchronized (receiveInfo)
		{
			receiveInfo.registerTopicListener(topicInfo, listener);
		}
	}

	private ChannelReceiveInfo getChannelReceiverInfo(String multicastIp, int multicastPort, String multicastGroup, String interfaceIp)
	{
		ChannelReceiveInfo receiveInfo = this.channelInfos.get(multicastGroup);
		if (receiveInfo == null)
		{
			synchronized (this)
			{
				receiveInfo = this.channelInfos.get(multicastGroup);
				if (receiveInfo == null)
				{
					receiveInfo = new ChannelReceiveInfo(multicastIp, multicastPort, multicastGroup, interfaceIp, this.bindPort, this.selectorThread);
					this.channelInfos.put(multicastGroup, receiveInfo);
				}
			}
		}
		return receiveInfo;
	}
}