
package ibxm;

/*
	ProTracker, Scream Tracker 3, FastTracker 2 Replay (c)2013 mumart@gmail.com
*/
public class IBXM {
	public static final String VERSION = "a65 (c)2013 mumart@gmail.com";

	private Module module;
	private int[] rampBuf;
	private Channel[] channels;
	private int sampleRate, interpolation, tickLen;
	private int seqPos, breakSeqPos, row, nextRow, tick;
	private int speed, plCount, plChannel;
	private GlobalVol globalVol;
	private Note note;

	/* Play the specified Module at the specified sampling rate. */
	public IBXM( Module module, int samplingRate ) {
		this.module = module;
		setSampleRate( samplingRate );
		interpolation = Channel.LINEAR;
		rampBuf = new int[ 128 ];
		channels = new Channel[ module.numChannels ];
		globalVol = new GlobalVol();
		note = new Note();
		setSequencePos( 0 );
	}

	/* Return the sampling rate of playback. */
	public int getSampleRate() {
		return sampleRate;
	}

	/* Set the sampling rate of playback. */
	public void setSampleRate( int rate ) {
		// Use with Module.c2Rate to adjust the tempo of playback.
		// To play at half speed, multiply both the samplingRate and Module.c2Rate by 2.
		if( rate < 8000 || rate > 128000 ) {
			throw new IllegalArgumentException( "Unsupported sampling rate!" );
		}
		sampleRate = rate;
	}

	/* Set the resampling quality to one of
	   Channel.NEAREST, Channel.LINEAR, or Channel.SINC. */
	public void setInterpolation( int interpolation ) {
		this.interpolation = interpolation;
	}

	/* Returns the length of the buffer required by getAudio(). */
	public int getMixBufferLength() {
		return ( 128000 * 10 / 32 ) + 260;
	}

	/* Get the current row position. */
	public int getRow() {
		return row;
	}

	/* Get the current pattern position in the sequence. */
	public int getSequencePos() {
		return seqPos;
	}

	/* Set the pattern in the sequence to play. The tempo is reset to the default. */
	public void setSequencePos( int pos ) {
		if( pos >= module.sequenceLength ) pos = 0;
		breakSeqPos = pos;
		nextRow = 0;
		tick = 1;
		globalVol.volume = module.defaultGVol;
		speed = module.defaultSpeed > 0 ? module.defaultSpeed : 6;
		setTempo( module.defaultTempo > 0 ? module.defaultTempo : 125 );
		plCount = plChannel = -1;
		for( int idx = 0; idx < module.numChannels; idx++ )
			channels[ idx ] = new Channel( module, idx, globalVol );
		for( int idx = 0; idx < 128; idx++ )
			rampBuf[ idx ] = 0;
		tick();
	}

	/* Returns the song duration in samples at the current sampling rate. */
	public int calculateSongDuration() {
		int duration = 0;
		setSequencePos( 0 );
		boolean songEnd = false;
		while( !songEnd ) {
			duration += tickLen;
			songEnd = tick();
		}
		setSequencePos( 0 );
		return duration;	
	}

	/* Seek to approximately the specified sample position.
	   The actual sample position reached is returned. */
	public int seek( int samplePos ) {
		setSequencePos( 0 );
		int currentPos = 0;
		while( ( samplePos - currentPos ) >= tickLen ) {
			for( int idx = 0; idx < module.numChannels; idx++ )
				channels[ idx ].updateSampleIdx( tickLen * 2, sampleRate * 2 );
			currentPos += tickLen;
			tick();
		}
		return currentPos;
	}

	/* Generate audio.
	   The number of samples placed into output_buf is returned.
	   The output buffer length must be at least that returned by get_mix_buffer_length().
	   A "sample" is a pair of 16-bit integer amplitudes, one for each of the stereo channels. */
	public int getAudio( int[] outputBuf ) {
		// Clear output buffer.
		for( int idx = 0, end = ( tickLen + 65 ) * 4; idx < end; idx++ )
			outputBuf[ idx ] = 0;
		// Resample.
		for( int chanIdx = 0; chanIdx < module.numChannels; chanIdx++ ) {
			Channel chan = channels[ chanIdx ];
			chan.resample( outputBuf, 0, ( tickLen + 65 ) * 2, sampleRate * 2, interpolation );
			chan.updateSampleIdx( tickLen * 2, sampleRate * 2 );
		}
		downsample( outputBuf, tickLen + 64 );
		volumeRamp( outputBuf );
		tick();
		return tickLen;
	}

	private void setTempo( int tempo ) {
		tickLen = ( sampleRate * 5 ) / ( tempo * 2 );
	}

	private void volumeRamp( int[] mixBuf ) {
		int rampRate = 256 * 2048 / sampleRate;
		for( int idx = 0, a1 = 0; a1 < 256; idx += 2, a1 += rampRate ) {
			int a2 = 256 - a1;
			mixBuf[ idx     ] = ( mixBuf[ idx     ] * a1 + rampBuf[ idx     ] * a2 ) >> 8;
			mixBuf[ idx + 1 ] = ( mixBuf[ idx + 1 ] * a1 + rampBuf[ idx + 1 ] * a2 ) >> 8;
		}
		System.arraycopy( mixBuf, tickLen * 2, rampBuf, 0, 128 );
	}

	private void downsample( int[] buf, int count ) {
		// 2:1 downsampling with simple but effective anti-aliasing. Buf must contain count * 2 + 1 stereo samples.
		int outLen = count * 2;
		for( int inIdx = 0, outIdx = 0; outIdx < outLen; inIdx += 4, outIdx += 2 ) {
			buf[ outIdx     ] = ( buf[ inIdx     ] >> 2 ) + ( buf[ inIdx + 2 ] >> 1 ) + ( buf[ inIdx + 4 ] >> 2 );
			buf[ outIdx + 1 ] = ( buf[ inIdx + 1 ] >> 2 ) + ( buf[ inIdx + 3 ] >> 1 ) + ( buf[ inIdx + 5 ] >> 2 );
		}
	}

	private boolean tick() {
		boolean songEnd = false;
		if( --tick <= 0 ) {
			tick = speed;
			songEnd = row();
		} else {
			for( int idx = 0; idx < module.numChannels; idx++ ) channels[ idx ].tick();
		}
		return songEnd;
	}

	private boolean row() {
		boolean songEnd = false;
		if( breakSeqPos >= 0 ) {
			if( breakSeqPos >= module.sequenceLength ) breakSeqPos = nextRow = 0;
			while( module.sequence[ breakSeqPos ] >= module.numPatterns ) {
				breakSeqPos++;
				if( breakSeqPos >= module.sequenceLength ) breakSeqPos = nextRow = 0;
			}
			if( breakSeqPos <= seqPos ) songEnd = true;
			seqPos = breakSeqPos;
			for( int idx = 0; idx < module.numChannels; idx++ ) channels[ idx ].plRow = 0;
			breakSeqPos = -1;
		}
		Pattern pattern = module.patterns[ module.sequence[ seqPos ] ];
		row = nextRow;
		if( row >= pattern.numRows ) row = 0;
		nextRow = row + 1;
		if( nextRow >= pattern.numRows ) {
			breakSeqPos = seqPos + 1;
			nextRow = 0;
		}
		int noteIdx = row * module.numChannels;
		for( int chanIdx = 0; chanIdx < module.numChannels; chanIdx++ ) {
			Channel channel = channels[ chanIdx ];
			pattern.getNote( noteIdx + chanIdx, note );
			if( note.effect == 0xE ) {
				note.effect = 0x70 | ( note.param >> 4 );
				note.param &= 0xF;
			}
			if( note.effect == 0x93 ) {
				note.effect = 0xF0 | ( note.param >> 4 );
				note.param &= 0xF;
			}
			if( note.effect == 0 && note.param > 0 ) note.effect = 0x8A;
			channel.row( note );
			switch( note.effect ) {
				case 0x81: /* Set Speed. */
					if( note.param > 0 ) tick = speed = note.param;
					break;
				case 0xB: case 0x82: /* Pattern Jump.*/
					if( plCount < 0 ) {
						breakSeqPos = note.param;
						nextRow = 0;
					}
					break;
				case 0xD: case 0x83: /* Pattern Break.*/
					if( plCount < 0 ) {
						breakSeqPos = seqPos + 1;
						nextRow = ( note.param >> 4 ) * 10 + ( note.param & 0xF );
					}
					break;
				case 0xF: /* Set Speed/Tempo.*/
					if( note.param > 0 ) {
						if( note.param < 32 ) tick = speed = note.param;
						else setTempo( note.param );
					}
					break;
				case 0x94: /* Set Tempo.*/
					if( note.param > 32 ) setTempo( note.param );
					break;
				case 0x76: case 0xFB : /* Pattern Loop.*/
					if( note.param == 0 ) /* Set loop marker on this channel. */
						channel.plRow = row;
					if( channel.plRow < row ) { /* Marker valid. Begin looping. */
						if( plCount < 0 ) { /* Not already looping, begin. */
							plCount = note.param;
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
					tick = speed + speed * note.param;
					break;
			}
		}
		return songEnd;
	}
}
