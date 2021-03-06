package com.clearpool.panda.core;

public enum PandaErrorCode
{
	EXCEPTION,
	PACKET_LOSS_UNABLE_TO_HANDLE_GAP,
	PACKET_LOSS_MAX_DROPS_EXCEEDED,
	PACKET_LOSS_RETRANSMISSION_FAILED,
	PACKET_LOSS_RETRANSMISSION_TIMEOUT,
	PACKET_LOSS_RETRANSMISSION_CORRUPTION,
	RETRANSMISSION_RESPONSE_PARTIAL,
	RETRANSMISSION_RESPONSE_NONE,
	RETRANSMISSION_DISABLED,
	NONE;
}
