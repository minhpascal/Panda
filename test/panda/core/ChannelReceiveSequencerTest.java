package panda.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import junit.framework.Assert;

import org.junit.Test;

import panda.utils.Utils;

@SuppressWarnings("static-method")
public class ChannelReceiveSequencerTest
{	
	@Test
	public void testSkipPacketAndDequeue() throws IOException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		//[1,1] - No Queue
		sequencer.packetReceived(true, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[3,10] - Queued
		for(int i=3;i<=10;i++)
		{
			sequencer.packetReceived(true, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(8, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//Declare drop
		sequencer.skipPacketAndDequeue(2);
		Assert.assertEquals(0, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(10, sequencer.getLastSequenceNumber());
		Assert.assertEquals(1, sequencer.getPacketsLost());
		Assert.assertEquals(0, sequencer.getPacketsDropped());
	}
	
	@Test
	public void testHandleGap() throws IOException, InterruptedException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		//[1,1] - No Queue
		sequencer.packetReceived(true, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[3,22] - Queued
		for(int i=3;i<=22;i++)
		{
			sequencer.packetReceived(true, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(20, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[23,23] - Will cause threshold to be crossed
		sequencer.packetReceived(true, 100, 23, (byte)3, createPacket(3));
		Assert.assertEquals(21, sequencer.getQueueSize());
		Assert.assertNotNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		Assert.assertEquals(sequencer.getGapRequestManager().getFirstSequenceNumberRequested(), 2);
		Assert.assertEquals(sequencer.getGapRequestManager().getPacketCountRequested(), 1);
		
		//[24,24] - Add to queue while recovering
		sequencer.packetReceived(true, 100, 24, (byte)3, createPacket(3));
		Assert.assertEquals(22, sequencer.getQueueSize());
		Assert.assertEquals(sequencer.getGapRequestManager().getFirstSequenceNumberRequested(), 2);
		Assert.assertEquals(sequencer.getGapRequestManager().getPacketCountRequested(), 1);
		long timeOfRequest = sequencer.getGapRequestManager().getTimeOfRequest();
		
		//[26,50] - Add to queue while recovering
		for(int i=26;i<=50;i++)
		{
			sequencer.packetReceived(true, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(47, sequencer.getQueueSize());
		Assert.assertEquals(sequencer.getGapRequestManager().getFirstSequenceNumberRequested(), 2);
		Assert.assertEquals(sequencer.getGapRequestManager().getPacketCountRequested(), 1);
		Assert.assertEquals(timeOfRequest, sequencer.getGapRequestManager().getTimeOfRequest());
		
		//declare 2 as dropped
		sequencer.closeRetransmissionManager();
		sequencer.skipPacketAndDequeue(2);
		Assert.assertEquals(24, sequencer.getLastSequenceNumber());
		Assert.assertEquals(25, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		
		Thread.sleep(1);
		
		//[51,51] - Add to queue while recovering
		sequencer.packetReceived(true, 100, 51, (byte)3, createPacket(3));
		Assert.assertEquals(26, sequencer.getQueueSize());
		Assert.assertEquals(sequencer.getGapRequestManager().getFirstSequenceNumberRequested(), 25);
		Assert.assertEquals(sequencer.getGapRequestManager().getPacketCountRequested(), 1);
		Assert.assertTrue(sequencer.getGapRequestManager().getTimeOfRequest() > timeOfRequest);
	}
	
	@Test
	public void testNoDrops() throws IOException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		for(int i=1; i<=1000; i++)
		{
			sequencer.packetReceived(true, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(0, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1000, sequencer.getLastSequenceNumber());
		Assert.assertEquals(0, sequencer.getPacketsLost());
		Assert.assertEquals(0, sequencer.getPacketsDropped());
	}
	
	@Test
	public void testSelfCorrectingOutOfSequence() throws IOException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		//[1,1] - No Queue
		sequencer.packetReceived(true, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[3,10] - Queued
		for(int i=3;i<=10;i++)
		{
			sequencer.packetReceived(true, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(8, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[2,2] - Fixed
		sequencer.packetReceived(true, 100, 2, (byte)3, createPacket(3));
		Assert.assertEquals(0, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(10, sequencer.getLastSequenceNumber());
		Assert.assertEquals(0, sequencer.getPacketsLost());
		Assert.assertEquals(0, sequencer.getPacketsDropped());
	}
	
	@Test
	public void testOutofSequenceThresholdNoRetransSupport() throws IOException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		//[1,1] - No Queue
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[3,22] - Queued
		for(int i=3;i<=22;i++)
		{
			sequencer.packetReceived(false, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(20, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[23,23] - Will cause threshold to be crossed
		sequencer.packetReceived(false, 100, 23, (byte)3, createPacket(3));
		Assert.assertEquals(23, sequencer.getLastSequenceNumber());
		Assert.assertEquals(1, sequencer.getPacketsLost());
		Assert.assertEquals(1, sequencer.getPacketsDropped());
	}
	
	@Test
	public void testOutofSequenceThresholdWithRetransSupport() throws IOException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		//[1,1] - No Queue
		sequencer.packetReceived(true, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[3,22] - Queued
		for(int i=3;i<=22;i++)
		{
			sequencer.packetReceived(true, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(20, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[23,23] - Will cause threshold to be crossed
		sequencer.packetReceived(true, 100, 23, (byte)3, createPacket(3));
		Assert.assertEquals(21, sequencer.getQueueSize());
		Assert.assertNotNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		Assert.assertEquals(sequencer.getGapRequestManager().getFirstSequenceNumberRequested(), 2);
		Assert.assertEquals(sequencer.getGapRequestManager().getPacketCountRequested(), 1);
		Assert.assertEquals(0, sequencer.getPacketsLost());
		Assert.assertEquals(1, sequencer.getPacketsDropped());
	}
	
	@Test
	public void testQueueGiveUpTimeNoRetransSupport() throws IOException, InterruptedException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		//[1,1] - No Queue
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[3,10] - Queued
		for(int i=3;i<=10;i++)
		{
			sequencer.packetReceived(false, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(8, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		Thread.sleep(3500);
		
		//[11] - Will cause threshold to be crossed
		sequencer.packetReceived(false, 100, 11, (byte)3, createPacket(3));
		Assert.assertEquals(0, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(11, sequencer.getLastSequenceNumber());
		Assert.assertEquals(1, sequencer.getPacketsLost());
		Assert.assertEquals(1, sequencer.getPacketsDropped());
	}
	
	@Test
	public void testQueueGiveUpTimeWithRetransSupport() throws IOException, InterruptedException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		//[1,1] - No Queue
		sequencer.packetReceived(true, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[3,10] - Queued
		for(int i=3;i<=10;i++)
		{
			sequencer.packetReceived(true, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(8, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		Thread.sleep(3500);
		
		//[11] - Will cause threshold to be crossed
		sequencer.packetReceived(true, 100, 11, (byte)3, createPacket(3));
		Assert.assertEquals(9, sequencer.getQueueSize());
		Assert.assertNotNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		Assert.assertEquals(sequencer.getGapRequestManager().getFirstSequenceNumberRequested(), 2);
		Assert.assertEquals(sequencer.getGapRequestManager().getPacketCountRequested(), 1);
		Assert.assertEquals(0, sequencer.getPacketsLost());
		Assert.assertEquals(1, sequencer.getPacketsDropped());
	}
	
	@Test
	public void testSenderRestart() throws IOException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 100);
		
		//Scenario 1 - restart after only receiving 1 packet
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		Assert.assertEquals(0, sequencer.getQueueSize());
		Assert.assertEquals(0, sequencer.getPacketsLost());
		Assert.assertEquals(0, sequencer.getPacketsDropped());
		
		//Scenario 2 - restart after only receiving 2 packets
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		sequencer.packetReceived(false, 100, 2, (byte)3, createPacket(3));
		Assert.assertEquals(2, sequencer.getLastSequenceNumber());
		
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		Assert.assertEquals(0, sequencer.getQueueSize());
		Assert.assertEquals(0, sequencer.getPacketsLost());
		Assert.assertEquals(0, sequencer.getPacketsDropped());
		
		//Scenario 3 - restart after having queued data
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		sequencer.packetReceived(false, 100, 3, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		Assert.assertEquals(1, sequencer.getQueueSize());
		
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		Assert.assertEquals(0, sequencer.getQueueSize());
		Assert.assertEquals(0, sequencer.getPacketsLost());
		Assert.assertEquals(0, sequencer.getPacketsDropped());
	}
	
	@Test
	public void testMaxDroppedPacketsAllowed() throws IOException
	{
		TestSelectorThread testSelectorThread = new TestSelectorThread();
		TestChannelReceiveInfo testChannelReceiveInfo = new TestChannelReceiveInfo("test1:1000", 1000, "test1:1000", "127.0.0.1", 1, testSelectorThread, 1000);
		ChannelReceiveSequencer sequencer = new ChannelReceiveSequencer(testSelectorThread, "test", "test1:1000", "test0", testChannelReceiveInfo, 2);
		
		//[1,1] - No Queue
		sequencer.packetReceived(false, 100, 1, (byte)3, createPacket(3));
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		
		//[3,22] - Queued
		for(int i=3;i<=22;i++)
		{
			sequencer.packetReceived(false, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(20, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(1, sequencer.getLastSequenceNumber());
		Assert.assertEquals(0, sequencer.getPacketsDropped());
		
		//[23,23] - Queued - causes drop
		sequencer.packetReceived(false, 100, 23, (byte)3, createPacket(3));
		Assert.assertEquals(23, sequencer.getLastSequenceNumber());
		Assert.assertEquals(1, sequencer.getPacketsDropped());
		
		//[25,44] - Queued
		for(int i=25;i<=44;i++)
		{
			sequencer.packetReceived(false, 100, i, (byte)3, createPacket(3));
		}
		Assert.assertEquals(20, sequencer.getQueueSize());
		Assert.assertNull(sequencer.getGapRequestManager());
		Assert.assertEquals(23, sequencer.getLastSequenceNumber());
		Assert.assertEquals(1, sequencer.getPacketsDropped());
		
		//[45,45] - Queued - causes drop
		sequencer.packetReceived(false, 100, 45, (byte)3, createPacket(3));
		Assert.assertEquals(45, sequencer.getLastSequenceNumber());
		Assert.assertEquals(2, sequencer.getPacketsDropped());
		
		//[47,47] - Queued - causes drop
		sequencer.packetReceived(false, 100, 47, (byte)3, createPacket(3));
		Assert.assertEquals(47, sequencer.getLastSequenceNumber());
		Assert.assertEquals(3, sequencer.getPacketsDropped());
		
		//[49,49] - Queued - causes drop
		sequencer.packetReceived(false, 100, 49, (byte)3, createPacket(3));
		Assert.assertEquals(49, sequencer.getLastSequenceNumber());
		Assert.assertEquals(4, sequencer.getPacketsDropped());
	}

	private static ByteBuffer createPacket(int messageCount)
	{
		ByteBuffer buffer = ByteBuffer.allocate(messageCount*(Utils.MESSAGE_HEADER_SIZE + 4)); //4 is the payloadsize
		for(int i=0; i<messageCount; i++)
		{
			buffer.putInt(i); //topicId
			buffer.putShort((short)4);
			buffer.putInt(i);
		}
		buffer.rewind();
		return buffer;
	}
}