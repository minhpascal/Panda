package panda.utils;


public class Utils 
{
	//Sizes
	public static final int MTU_SIZE = 1500;
	public static final int MAX_TCP_SIZE = 65535;
	public static final byte PACKET_HEADER_SIZE = 11;
	public static final int MESSAGE_HEADER_SIZE = 4; //topicId - 2 bytes, message length - 2 bytes
	public static final int NETWORK_HEADER_SIZE = 54; //Ethernet header - 26 bytes, IP header - 20 bytes, UDP header - 8 bytes
	public static final int MAX_MESSAGE_PAYLOAD_SIZE = MTU_SIZE - PACKET_HEADER_SIZE - MESSAGE_HEADER_SIZE - NETWORK_HEADER_SIZE;
	public static final int MAX_PACKET_PAYLOAD_SIZE = MTU_SIZE - PACKET_HEADER_SIZE - NETWORK_HEADER_SIZE;
	public static final int RETRANSMISSION_RESPONSE_HEADER_SIZE = 12;
	public static final int RETRANSMISSION_RESPONSE_PACKET_HEADER_SIZE = 4;
	public static final int RETRANSMISSION_REQUEST_HEADER_SIZE = 12;
	public static final byte START_OF_RETRANSMISSION = 2;
	public static final byte END_OF_RETRANSMISSION = 3;
}