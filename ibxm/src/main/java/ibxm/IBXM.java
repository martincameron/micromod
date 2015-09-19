
package ibxm;

import micromod.AbstractReplay;
import micromod.ChannelInterpolation;
import micromod.Note;

/*
	ProTracker, Scream Tracker 3, FastTracker 2 Replay (c)2015 mumart@gmail.com
*/
public class IBXM extends AbstractReplay<Module, Pattern, Instrument, Channel> {
	public static final String VERSION = "a71 (c)2015 mumart@gmail.com";

	private Channel[] channels;
	private int breakSeqPos, nextRow;
	private int plCount, plChannel;
	private GlobalVol globalVol;
	private Note note;

	/* Play the specified Module at the specified sampling rate. */
	public IBXM( Module module, int samplingRate ) {
		super( module );
		setSampleRate( samplingRate );
		setInterpolation( ChannelInterpolation.LINEAR );
		channels = new Channel[ module.getNumChannels() ];
		globalVol = new GlobalVol();
		note = new Note();
		setSequencePos( 0 );
	}

	@Override
	protected int getNumChannels() {
		return module.getNumChannels();
	}

	@Override
	protected Channel getChannel( int idx ) {
		return channels[idx];
	}

	/* Set the pattern in the sequence to play. The tempo is reset to the default. */
	public void setSequencePos( int pos ) {
		if( pos >= module.getSequenceLength() ) pos = 0;
		breakSeqPos = pos;
		nextRow = 0;
		tick = 1;
		globalVol.volume = module.defaultGVol;
		speed = module.defaultSpeed > 0 ? module.defaultSpeed : 6;
		tempo = module.defaultTempo > 0 ? module.defaultTempo : 125;
		plCount = plChannel = -1;
		for( int idx = 0; idx < playCount.length; idx++ ) {
			int patIdx = module.getSequenceEntry( idx );
			int numRows = ( patIdx < module.getNumPatterns() ) ? module.getPattern( patIdx ).getNumRows() : 0;
			playCount[ idx ] = new byte[ numRows ];
		}
		for( int idx = 0; idx < module.getNumChannels(); idx++ )
			channels[ idx ] = new Channel( module, idx, globalVol );
		for( int idx = 0; idx < 128; idx++ )
			rampBuf[ idx ] = 0;
		tick();
	}

	@Override
	protected void row() {
		if( breakSeqPos >= 0 ) {
			if( breakSeqPos >= module.getSequenceLength() ) breakSeqPos = nextRow = 0;
			while( module.getSequenceEntry( breakSeqPos ) >= module.getNumPatterns() ) {
				breakSeqPos++;
				if( breakSeqPos >= module.getSequenceLength() ) breakSeqPos = nextRow = 0;
			}
			seqPos = breakSeqPos;
			for( int idx = 0; idx < module.getNumChannels(); idx++ ) channels[ idx ].plRow = 0;
			breakSeqPos = -1;
		}
		Pattern pattern = module.getPattern( module.getSequenceEntry( seqPos ) );
		row = nextRow;
		if( row >= pattern.getNumRows() ) row = 0;
		int count = playCount[ seqPos ][ row ];
		if( plCount < 0 && count < 127 ) {
			playCount[ seqPos ][ row ] = ( byte ) ( count + 1 );
		}
		nextRow = row + 1;
		if( nextRow >= pattern.getNumRows() ) {
			breakSeqPos = seqPos + 1;
			nextRow = 0;
		}
		int noteIdx = row * module.getNumChannels();
		for( int chanIdx = 0; chanIdx < module.getNumChannels(); chanIdx++ ) {
			Channel channel = channels[ chanIdx ];
			pattern.getNote( noteIdx + chanIdx, 0, note );
			if( note.effect == 0xE ) {
				note.effect = 0x70 | ( note.parameter >> 4 );
				note.parameter &= 0xF;
			}
			if( note.effect == 0x93 ) {
				note.effect = 0xF0 | ( note.parameter >> 4 );
				note.parameter &= 0xF;
			}
			if( note.effect == 0 && note.parameter > 0 ) note.effect = 0x8A;
			channel.row( note );
			switch( note.effect ) {
				case 0x81: /* Set Speed. */
					if( note.parameter > 0 )
						tick = speed = note.parameter;
					break;
				case 0xB: case 0x82: /* Pattern Jump.*/
					if( plCount < 0 ) {
						breakSeqPos = note.parameter;
						nextRow = 0;
					}
					break;
				case 0xD: case 0x83: /* Pattern Break.*/
					if( plCount < 0 ) {
						if( breakSeqPos < 0 )
							breakSeqPos = seqPos + 1;
						nextRow = ( note.parameter >> 4 ) * 10 + ( note.parameter & 0xF );
					}
					break;
				case 0xF: /* Set Speed/Tempo.*/
					if( note.parameter > 0 ) {
						if( note.parameter < 32 )
							tick = speed = note.parameter;
						else
							tempo = note.parameter;
					}
					break;
				case 0x94: /* Set Tempo.*/
					if( note.parameter > 32 )
						tempo = note.parameter;
					break;
				case 0x76: case 0xFB : /* Pattern Loop.*/
					if( note.parameter == 0 ) /* Set loop marker on this channel. */
						channel.plRow = row;
					if( channel.plRow < row ) { /* Marker valid. Begin looping. */
						if( plCount < 0 ) { /* Not already looping, begin. */
							plCount = note.parameter;
							plChannel = chanIdx;
						}
						if( plChannel == chanIdx ) { /* Next Loop.*/
							if( plCount == 0 ) { /* Loop finished. */
								/* Invalidate current marker. */
								channel.plRow = row + 1;
							} else { /* Loop and cancel any breaks on this row. */
								nextRow = channel.plRow;
								breakSeqPos = -1;
							}
							plCount--;
						}
					}
					break;
				case 0x7E: case 0xFE: /* Pattern Delay.*/
					tick = speed + speed * note.parameter;
					break;
			}
		}
	}
}
