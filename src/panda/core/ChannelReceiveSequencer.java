package panda.core;

import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.logging.Logger;

import panda.core.containers.Packet;

//TODO - FEATURE - track number of requests
//TODO - REQUIREMENT - install timeout + min packet queue to build before requesting + handle gaps while retrans
public class ChannelReceiveSequencer
{
	private static final Logger LOGGER = Logger.getLogger(ChannelReceiveSequencer.class.getName());

	private final SelectorThread selectorThread;
	private final String key;
	private final String multicastGroup;
	private final String sourceIp;
	private final ChannelReceiveInfo channelReceiveInfo;
	private final PriorityQueue<Packet> queuedPackets;

	private long lastSequenceNumber;
	private GapRequestManager retransmissionManager;

	public ChannelReceiveSequencer(SelectorThread selectorThread, String key, String multicastGroup, String sourceIp, ChannelReceiveInfo channelReceiveInfo)
	{
		this.selectorThread = selectorThread;
		this.key = key;
		this.multicastGroup = multicastGroup;
		this.sourceIp = sourceIp;
		this.channelReceiveInfo = channelReceiveInfo;
		this.queuedPackets = new PriorityQueue<Packet>();

		this.lastSequenceNumber = 0;
		this.retransmissionManager = null;
	}

	// Called by selectorThread
	public void packetReceived(boolean supportsRetranmissions, int retransmissionPort, long sequenceNumber, byte messageCount, ByteBuffer packetBuffer)
	{
		if (sequenceNumber == this.lastSequenceNumber + 1 || this.lastSequenceNumber == 0)
		{
			this.lastSequenceNumber = sequenceNumber;
			this.channelReceiveInfo.parseAndDeliverToListeners(messageCount, packetBuffer);
			dequeueQueuedPackets();
		}
		else if (sequenceNumber == 1)
		{
			this.lastSequenceNumber = sequenceNumber;
			this.channelReceiveInfo.parseAndDeliverToListeners(messageCount, packetBuffer);
			this.queuedPackets.clear();
			if (this.retransmissionManager != null)
			{
				this.retransmissionManager.close();
			}
		}
		else
		{
			if (supportsRetranmissions)
			{
				boolean success = this.handleGap(sequenceNumber, retransmissionPort, messageCount, packetBuffer);
				if (success)
					return;
				LOGGER.info("Failed to handle gap for messages starting seq. " + sequenceNumber + " for a total of " + messageCount + " messages"); // CHINMAY
																																					// 03272013
			}
			// CHINMAY 03272013 - Wrong message will be logged below if
			// Retransmission is supported but fails.
			LOGGER.severe("Gap detected.  Source=" + this.key + " Expected=" + (this.lastSequenceNumber + 1) + " Received=" + sequenceNumber + ". Retransmission not supported, skipping packets");
			this.channelReceiveInfo.parseAndDeliverToListeners(messageCount, packetBuffer);
			skipPacketAndDequeue(sequenceNumber);
		}
	}

	private void dequeueQueuedPackets()
	{
		if (this.queuedPackets.size() == 0)
			return;
		Packet queuedPacket = this.queuedPackets.peek();
		while (queuedPacket != null)
		{
			if (queuedPacket.getSequenceNumber() <= this.lastSequenceNumber)
			{
				this.queuedPackets.remove();
			}
			else if (queuedPacket.getSequenceNumber() == this.lastSequenceNumber + 1)
			{
				queuedPacket = this.queuedPackets.remove();
				this.channelReceiveInfo.parseAndDeliverToListeners(queuedPacket.getMessageCount(), ByteBuffer.wrap(queuedPacket.getBytes()));
				this.lastSequenceNumber = queuedPacket.getSequenceNumber();
			}
			else
			{
				return;
			}
			queuedPacket = this.queuedPackets.peek();
		}
	}

	private boolean handleGap(long sequenceNumber, int retransmissionPort, byte messageCount, ByteBuffer packetBuffer)
	{
		LOGGER.info("Handling gap starting message seq. " + sequenceNumber + " for a total of " + messageCount + " messages"); // CHINMAY 03272013
		byte[] bytes = new byte[packetBuffer.remaining()];
		int packetBufferPosition = packetBuffer.position(); // CHINMAY 04012013 - to restore position() in case of this.retransmissionManager.doNotConnectToTCP == true
		packetBuffer.get(bytes);
		Packet packet = new Packet(sequenceNumber, messageCount, bytes);
		this.queuedPackets.add(packet);

		Packet headPacket = this.queuedPackets.peek();
		if (headPacket.getSequenceNumber() > this.lastSequenceNumber + 1)
		{
			int packetCount = (int) (headPacket.getSequenceNumber() - this.lastSequenceNumber - 1);
			if (this.retransmissionManager == null)
			{
				this.retransmissionManager = new GapRequestManager(this.selectorThread, this.multicastGroup, this.sourceIp, this);
			}
			// return
			// this.retransmissionManager.sendGapRequest(retransmissionPort,
			// this.lastSequenceNumber + 1, packetCount); // CHINMAY 04012013
			if (this.retransmissionManager.doNotConnectToTCP == false)
			{
				return this.retransmissionManager.sendGapRequest(retransmissionPort, this.lastSequenceNumber + 1, packetCount);
			}
			packetBuffer.position(packetBufferPosition); // CHINMAY 04012013 - for passing back to parseAndDeliverToListeners()
		}
		LOGGER.severe("This should never happen");
		return false;
	}

	public void skipPacketAndDequeue(long sequenceNumber)
	{
		this.lastSequenceNumber = sequenceNumber;
		dequeueQueuedPackets();
	}

	// Called by selectorThread
	public ChannelReceiveInfo getChannelReceiveInfo()
	{
		return this.channelReceiveInfo;
	}

	public void closeRetransmissionManager()
	{
		this.retransmissionManager = null;
	}

	public String getKey()
	{
		return this.key;
	}
}