
package micromod;

/*
	Java ProTracker Replay (c)2015 mumart@gmail.com
*/
public class Micromod extends AbstractReplay<Module, Pattern, Instrument, Channel> {
	public static final String VERSION = "20150717 (c)2015 mumart@gmail.com";

	private Note note;
	private Channel[] channels;
	private int breakSeqPos, nextRow;
	private int plCount, plChannel;

	/* Play the specified Module at the specified sampling rate. */
	public Micromod( Module module, int samplingRate ) {
		super( module );
		setSampleRate( samplingRate );
		note = new Note();
		channels = new Channel[ module.getNumChannels() ];
		setSequencePos( 0 );
	}

	/* Enable or disable the linear interpolation filter. */
	public void setInterpolation( ChannelInterpolation interpolation ) {
		if (interpolation != ChannelInterpolation.LINEAR && interpolation != ChannelInterpolation.NONE) {
			throw new RuntimeException( "Only NONE and LINEAR interpolations supported" );
		}
		super.setInterpolation( interpolation );
	}

	@Override
	protected int getNumChannels() {
		return channels.length;
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
		speed = 6;
		tempo = 125;
		plCount = plChannel = -1;
		for( int idx = 0; idx < playCount.length; idx++ )
			playCount[ idx ] = new byte[ Pattern.NUM_ROWS ];
		for( int idx = 0; idx < channels.length; idx++ )
			channels[ idx ] = new Channel( module, idx );
		for( int idx = 0; idx < 128; idx++ )
			rampBuf[ idx ] = 0;
		tick();
	}

	@Override
	protected void row() {
		if( breakSeqPos >= 0 ) {
			if( breakSeqPos >= module.getSequenceLength() ) breakSeqPos = nextRow = 0;
			seqPos = breakSeqPos;
			for( int idx = 0; idx < channels.length; idx++ ) channels[ idx ].plRow = 0;
			breakSeqPos = -1;
		}
		row = nextRow;
		int count = playCount[ seqPos ][ row ];
		if( plCount < 0 && count < 127 ) {
			playCount[ seqPos ][ row ] = ( byte ) ( count + 1 );
		}
		nextRow = row + 1;
		if( nextRow >= 64 ) {
			breakSeqPos = seqPos + 1;
			nextRow = 0;
		}
		for( int chanIdx = 0; chanIdx < channels.length; chanIdx++ ) {
			Channel channel = channels[ chanIdx ];
			module.getPattern( module.getSequenceEntry( seqPos ) ).getNote( row, chanIdx, note );
			note.effect &= 0xFF;
			note.parameter &= 0xFF;
			if( note.effect == 0xE ) {
				note.effect = 0x10 | ( note.parameter >> 4 );
				note.parameter &= 0xF;
			}
			if( note.effect == 0 && note.parameter > 0 ) note.effect = 0xE;
			channel.row( note );
			switch( note.effect ) {
				case 0xB: /* Pattern Jump.*/
					if( plCount < 0 ) {
						breakSeqPos = note.parameter;
						nextRow = 0;
					}
					break;
				case 0xD: /* Pattern Break.*/
					if( plCount < 0 ) {
						if( breakSeqPos < 0 ) breakSeqPos = seqPos + 1;
						nextRow = ( note.parameter >> 4 ) * 10 + ( note.parameter & 0xF );
						if( nextRow >= 64 ) nextRow = 0;
					}
					break;
				case 0xF: /* Set Speed.*/
					if( note.parameter > 0 ) {
						if( note.parameter < 32 )
							tick = speed = note.parameter;
						else
							tempo = note.parameter;
					}
					break;
				case 0x16: /* Pattern Loop.*/
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
				case 0x1E: /* Pattern Delay.*/
					tick = speed + speed * note.parameter;
					break;
			}
		}
	}
}
