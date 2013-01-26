
package ibxm;

/*
	ProTracker, Scream Tracker 3, FastTracker 2 Replay (c)2013 mumart@gmail.com
*/
public class IBXM {
	public static final String VERSION = "a63 (c)2013 mumart@gmail.com";

	private Module module;
	private int[] rampBuf;
	private Channel[] channels;
	private boolean oversample;
	private int interpolation, filtL, filtR;
	private int sampleRate, tickLen, rampRate;
	private int seqPos, breakSeqPos, row, nextRow, tick;
	private int speed, plCount, plChannel;
	private GlobalVol globalVol;
	private Note note;

	/* Play the specified Module at the specified sampling rate. */
	public IBXM( Module module, int samplingRate ) {
		this.module = module;
		setSampleRate( samplingRate );
		interpolation = Channel.LINEAR;
		rampBuf = new int[ 256 ];
		channels = new Channel[ module.numChannels ];
		globalVol = new GlobalVol();
		note = new Note();
		setSequencePos( 0 );
	}

	/* Return the sampling rate of playback. */
	public int getSampleRate() {
		return oversample ? ( sampleRate >> 1 ) : sampleRate;
	}

	/* Set the sampling rate of playback. */
	public void setSampleRate( int rate ) {
	   // Use with Module.c2Rate to adjust the tempo of playback.
	   // To play at half speed, multiply both the samplingRate and Module.c2Rate by 2.
	   if( rate < 8000 || rate > 256000 ) {
		throw new IllegalArgumentException( "Unsupported sampling rate!" );
	   }
	   oversample = rate < 128000;
	   sampleRate = rate << ( oversample ? 1 : 0 );
	   rampRate = 256 / ( sampleRate >> 11 );
	}

	/* Set the resampling quality to one of
	   Channel.NEAREST, Channel.LINEAR, or Channel.SINC. */
	public void setInterpolation( int interpolation ) {
		this.interpolation = interpolation;
	}

	/* Returns the length of the buffer required by getAudio(). */
	public int getMixBufferLength() {
		return ( 256000 * 5 / 32 ) + 256;
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
			channels[ idx ] = new Channel( module, idx, sampleRate, globalVol );
		for( int idx = 0; idx < 256; idx++ )
			rampBuf[ idx ] = 0;
		filtL = filtR = 0;
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
		return oversample ? ( duration >> 1 ) : duration;	
	}

	/* Seek to approximately the specified sample position.
	   The actual sample position reached is returned. */
	public int seek( int samplePos ) {
		samplePos = oversample ? ( samplePos << 1 ) : samplePos;
		setSequencePos( 0 );
		int currentPos = 0;
		while( ( samplePos - currentPos ) >= tickLen ) {
			for( int idx = 0; idx < module.numChannels; idx++ )
				channels[ idx ].updateSampleIdx( tickLen );
			currentPos += tickLen;
			tick();
		}
		return oversample ? ( currentPos >> 1 ) : currentPos;
	}

	/* Generate audio.
	   The number of samples placed into output_buf is returned.
	   The output buffer length must be at least that returned by get_mix_buffer_length().
	   A "sample" is a pair of 16-bit integer amplitudes, one for each of the stereo channels. */
	public int getAudio( int[] outputBuf ) {
		// Clear output buffer.
		for( int idx = 0, end = ( tickLen + 128 ) << 1; idx < end; idx++ )
			outputBuf[ idx ] = 0;
		// Resample.
		for( int chanIdx = 0; chanIdx < module.numChannels; chanIdx++ ) {
			Channel chan = channels[ chanIdx ];
			chan.resample( outputBuf, 0, tickLen + 128, interpolation );
			chan.updateSampleIdx( tickLen );
		}
		volumeRamp( outputBuf );
		tick();
		return downsample( outputBuf, tickLen );
	}

	private void setTempo( int tempo ) {
		// Make sure tick length is even to simplify 2x oversampling.
		tickLen = ( ( sampleRate * 5 ) / ( tempo * 2 ) ) & -2;
	}

	private void volumeRamp( int[] mixBuf ) {
		for( int idx = 0, a1 = 0; a1 < 256; idx += 2, a1 += rampRate ) {
			int a2 = 256 - a1;
			mixBuf[ idx     ] = ( mixBuf[ idx     ] * a1 + rampBuf[ idx     ] * a2 ) >> 8;
			mixBuf[ idx + 1 ] = ( mixBuf[ idx + 1 ] * a1 + rampBuf[ idx + 1 ] * a2 ) >> 8;
		}
		System.arraycopy( mixBuf, tickLen << 1, rampBuf, 0, 256 );
	}

	private int downsample( int[] buf, int count ) {
		// 2:1 downsampling with simple but effective anti-aliasing.
		// Count is the number of stereo samples to process, and must be even.
		int fl = filtL, fr = filtR;
		int inIdx = 0, outIdx = 0;
		while( outIdx < count ) {	
			int outL = fl + ( buf[ inIdx++ ] >> 1 );
			int outR = fr + ( buf[ inIdx++ ] >> 1 );
			fl = buf[ inIdx++ ] >> 2;
			fr = buf[ inIdx++ ] >> 2;
			buf[ outIdx++ ] = outL + fl;
			buf[ outIdx++ ] = outR + fr;
		}
		filtL = fl;
		filtR = fr;
		return count >> 1;
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
