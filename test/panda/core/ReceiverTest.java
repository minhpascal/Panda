package panda.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Date;

import panda.core.containers.TopicInfo;


public class ReceiverTest
{
	public static void main(String[] args) throws Exception
	{
		final PandaAdapter adapter = new PandaAdapter(0);
		final TopicInfo topicInfo1 = new TopicInfo("239.9.9.9", Integer.valueOf(9001), Integer.valueOf((short)1), "FIVE");
		final TopicInfo topicInfo2 = new TopicInfo("239.9.9.9", Integer.valueOf(9001), Integer.valueOf((short)2), "FOUR");
		
		adapter.subscribe(topicInfo1, getLocalIp(null), new PandaDataListener() {
			@Override
			public void receivedPandaData(int topicId, ByteBuffer payload)
			{
				byte[] bytes = new byte[payload.remaining()];
				payload.get(bytes, 0, bytes.length);
				String string = new String(bytes);
				int stringValue = Integer.parseInt(string);
				if (stringValue % 1 == 0) System.out.println(new Date() + " Received packet=" + string);
			}

			@Override
			public void receivedPandaError(PandaErrorCode issueCode, String message, Throwable throable)
			{
				System.err.println(issueCode + "|" + message);
			}
		}, 10000000);
		adapter.subscribe(topicInfo2, getLocalIp(null), new PandaDataListener() {
			@Override
			public void receivedPandaData(int topicId, ByteBuffer payload)
			{
				byte[] bytes = new byte[payload.remaining()];
				payload.get(bytes, 0, bytes.length);
				String string = new String(bytes);
				int stringValue = Integer.parseInt(string);
				if (stringValue % 1 == 0) System.out.println(new Date() + " Received packet=" + string);
			}

			@Override
			public void receivedPandaError(PandaErrorCode issueCode, String message, Throwable throable)
			{
				System.err.println(issueCode + "|" + message);
			}
		}, 10000000);
	}

	private static String getLocalIp(String ip)
	{
		if (ip == null)
		{
			try
			{
				return InetAddress.getLocalHost().getHostAddress();
			}
			catch (UnknownHostException e)
			{
				return null;
			}
		}
		return ip;
	}
}