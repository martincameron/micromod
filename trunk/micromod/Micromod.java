
package micromod;

/*
	Java ProTracker Replay (c)2013 mumart@gmail.com
*/
public class Micromod {
	public static final String VERSION = "20130124 (c)2013 mumart@gmail.com";

	private Module module;
	private int[] rampBuf;
	private Channel[] channels;
	private int tickLen, logRampLen;
	private int seqPos, breakSeqPos, row, nextRow, tick;
	private int speed, plCount, plChannel;
	private int sampleRate, filtL, filtR;
	private boolean interpolation, oversample;

	/* Play the specified Module at the specified sampling rate. */
	public Micromod( Module module, int samplingRate ) {
		this.module = module;
		if( samplingRate < 8000 )
			throw new IllegalArgumentException( "Unsupported sampling rate!" );
		oversample = samplingRate < 64000;
		sampleRate = oversample ? samplingRate << 1 : samplingRate;
		logRampLen = 8;
		while( ( 1024 << logRampLen ) > sampleRate )
			logRampLen = logRampLen - 1; 
		rampBuf = new int[ 2 << logRampLen ];
		channels = new Channel[ module.numChannels ];
		setSequencePos( 0 );
	}

	/* Return the sampling rate of playback. */
	public int getSampleRate() {
		return oversample ? ( sampleRate >> 1 ) : sampleRate;
	}

	/* Enable or disable the linear interpolation filter. */
	public void setInterpolation( boolean interpolation ) {
		this.interpolation = interpolation;
	}

	/* Return the maximum length of the buffer required by getAudio(). */
	public int getMixBufferLength() {
		return ( sampleRate * 5 / 32 ) + ( 2 << logRampLen );
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
		speed = 6;
		setTempo( 125 );
		plCount = plChannel = -1;
		for( int idx = 0; idx < module.numChannels; idx++ )
			channels[ idx ] = new Channel( module, idx, sampleRate );
		for( int idx = 0, end = 2 << logRampLen; idx < end; idx++ )
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

	/*
		Seek to approximately the specified sample position.
		The actual sample position reached is returned.
	*/
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

	/*
		Generate audio.
		The number of samples placed into outputBuf is returned.
		The output buffer length must be at least that returned by getMixBufferLength().
		A "sample" is a pair of 16-bit integer amplitudes, one for each of the stereo channels.
	*/
	public int getAudio( int[] outputBuf ) {
		int outLen = tickLen + ( 1 << logRampLen );
		// Clear output buffer.
		for( int idx = 0, end = outLen << 1; idx < end; idx++ )
			outputBuf[ idx ] = 0;
		// Resample.
		for( int chanIdx = 0; chanIdx < module.numChannels; chanIdx++ ) {
			Channel chan = channels[ chanIdx ];
			chan.resample( outputBuf, 0, outLen, interpolation );
			chan.updateSampleIdx( tickLen );
		}
		volumeRamp( outputBuf );
		tick();
		return downsample( outputBuf, tickLen );
	}

	private void setTempo( int tempo ) {
		// Make sure tick length is even to simplify downsampling.
		tickLen = ( ( sampleRate * 5 ) / ( tempo * 2 ) ) & -2;
	}

	private void volumeRamp( int[] mixBuf ) {
		int rampBufLen = 2 << logRampLen;
		for( int idx = 0; idx < rampBufLen; idx += 2 ) {
			int a1 = ( idx * 128 ) >> logRampLen;
			int a2 = 256 - a1;
			mixBuf[ idx     ] = ( mixBuf[ idx     ] * a1 + rampBuf[ idx     ] * a2 ) >> 8;
			mixBuf[ idx + 1 ] = ( mixBuf[ idx + 1 ] * a1 + rampBuf[ idx + 1 ] * a2 ) >> 8;
		}
		System.arraycopy( mixBuf, tickLen << 1, rampBuf, 0, rampBufLen );
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
			if( breakSeqPos <= seqPos ) songEnd = true;
			seqPos = breakSeqPos;
			for( int idx = 0; idx < module.numChannels; idx++ ) channels[ idx ].plRow = 0;
			breakSeqPos = -1;
		}
		row = nextRow;
		nextRow = row + 1;
		if( nextRow >= 64 ) {
			breakSeqPos = seqPos + 1;
			nextRow = 0;
		}
		int patOffset = ( module.sequence[ seqPos ] * 64 + row ) * module.numChannels * 4;
		for( int chanIdx = 0; chanIdx < module.numChannels; chanIdx++ ) {
			Channel channel = channels[ chanIdx ];
			int key = module.patterns[ patOffset ] & 0xFF;
			int ins = module.patterns[ patOffset + 1 ] & 0xFF;
			int effect = module.patterns[ patOffset + 2 ] & 0xFF;
			int param  = module.patterns[ patOffset + 3 ] & 0xFF;
			patOffset += 4;
			if( effect == 0xE ) {
				effect = 0x10 | ( param >> 4 );
				param &= 0xF;
			}
			if( effect == 0 && param > 0 ) effect = 0xE;
			channel.row( key, ins, effect, param );
			switch( effect ) {
				case 0xB: /* Pattern Jump.*/
					if( plCount < 0 ) {
						breakSeqPos = param;
						nextRow = 0;
					}
					break;
				case 0xD: /* Pattern Break.*/
					if( plCount < 0 ) {
						breakSeqPos = seqPos + 1;
						nextRow = ( param >> 4 ) * 10 + ( param & 0xF );
						if( nextRow >= 64 ) nextRow = 0;
					}
					break;
				case 0xF: /* Set Speed.*/
					if( param > 0 ) {
						if( param < 32 ) tick = speed = param;
						else setTempo( param );
					}
					break;
				case 0x16: /* Pattern Loop.*/
					if( param == 0 ) /* Set loop marker on this channel. */
						channel.plRow = row;
					if( channel.plRow < row ) { /* Marker valid. Begin looping. */
						if( plCount < 0 ) { /* Not already looping, begin. */
							plCount = param;
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
					tick = speed + speed * param;
					break;
			}
		}
		return songEnd;
	}
}
