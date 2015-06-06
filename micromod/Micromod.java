
package micromod;

/*
	Java ProTracker Replay (c)2015 mumart@gmail.com
*/
public class Micromod {
	public static final String VERSION = "20150606 (c)2015 mumart@gmail.com";

	private Module module;
	private int[] rampBuf;
	private Note note;
	private Channel[] channels;
	private int sampleRate;
	private int seqPos, breakSeqPos, row, nextRow, tick;
	private int speed, tempo, plCount, plChannel;
	private boolean interpolation;

	/* Play the specified Module at the specified sampling rate. */
	public Micromod( Module module, int samplingRate ) {
		this.module = module;
		setSampleRate( samplingRate );
		rampBuf = new int[ 128 ];
		note = new Note();
		channels = new Channel[ module.getNumChannels() ];
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

	/* Enable or disable the linear interpolation filter. */
	public void setInterpolation( boolean interpolation ) {
		this.interpolation = interpolation;
	}

	/* Return the length of the buffer required by getAudio(). */
	public int getMixBufferLength() {
		return ( calculateTickLen( 32, 128000 ) + 65 ) * 4;
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
		if( pos >= module.getSequenceLength() ) pos = 0;
		breakSeqPos = pos;
		nextRow = 0;
		tick = 1;
		speed = 6;
		tempo = 125;
		plCount = plChannel = -1;
		for( int idx = 0; idx < channels.length; idx++ )
			channels[ idx ] = new Channel( module, idx );
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
			duration += calculateTickLen( tempo, sampleRate );
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
		int tickLen = calculateTickLen( tempo, sampleRate );
		while( ( samplePos - currentPos ) >= tickLen ) {
			for( int idx = 0; idx < channels.length; idx++ )
				channels[ idx ].updateSampleIdx( tickLen * 2, sampleRate * 2 );
			currentPos += tickLen;
			tick();
			tickLen = calculateTickLen( tempo, sampleRate );
		}
		return currentPos;
	}

	/* Seek to the specified position and row in the sequence. */
	public void seekSequencePos( int sequencePos, int sequenceRow ) {
		setSequencePos( 0 );
		if( sequencePos < 0 || sequencePos >= module.getSequenceLength() )
			sequencePos = 0;
		if( sequenceRow >= 64 )
			sequenceRow = 0;
		while( seqPos < sequencePos || row < sequenceRow ) {
			int tickLen = calculateTickLen( tempo, sampleRate );
			for( int idx = 0; idx < channels.length; idx++ )
				channels[ idx ].updateSampleIdx( tickLen * 2, sampleRate * 2 );
			if( tick() ) {
				// Song end reached.
				setSequencePos( sequencePos );
				return;
			}
		}
	}

	/* Generate audio.
	   The number of samples placed into outputBuf is returned.
	   The output buffer length must be at least that returned by getMixBufferLength().
	   A "sample" is a pair of 16-bit integer amplitudes, one for each of the stereo channels. */
	public int getAudio( int[] outputBuf ) {
		int tickLen = calculateTickLen( tempo, sampleRate );
		// Clear output buffer.
		for( int idx = 0, end = ( tickLen + 65 ) * 4; idx < end; idx++ )
			outputBuf[ idx ] = 0;
		// Resample.
		for( int chanIdx = 0; chanIdx < channels.length; chanIdx++ ) {
			Channel chan = channels[ chanIdx ];
			chan.resample( outputBuf, 0, ( tickLen + 65 ) * 2, sampleRate * 2, interpolation );
			chan.updateSampleIdx( tickLen * 2, sampleRate * 2 );
		}
		downsample( outputBuf, tickLen + 64 );
		volumeRamp( outputBuf, tickLen );
		tick();
		return tickLen;
	}

	private int calculateTickLen( int tempo, int samplingRate ) {
		return ( samplingRate * 5 ) / ( tempo * 2 );
	}

	private void volumeRamp( int[] mixBuf, int tickLen ) {
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
			for( int idx = 0; idx < channels.length; idx++ ) channels[ idx ].tick();
		}
		return songEnd;
	}

	private boolean row() {
		boolean songEnd = false;
		if( breakSeqPos >= 0 ) {
			if( breakSeqPos >= module.getSequenceLength() ) breakSeqPos = nextRow = 0;
			if( breakSeqPos <= seqPos ) songEnd = true;
			seqPos = breakSeqPos;
			for( int idx = 0; idx < channels.length; idx++ ) channels[ idx ].plRow = 0;
			breakSeqPos = -1;
		}
		row = nextRow;
		nextRow = row + 1;
		if( nextRow >= 64 ) {
			breakSeqPos = seqPos + 1;
			nextRow = 0;
		}
		for( int chanIdx = 0; chanIdx < channels.length; chanIdx++ ) {
			Channel channel = channels[ chanIdx ];
			module.getPattern( module.getSequenceEntry( seqPos ) ).getNote( row, chanIdx, note );
			int effect = note.effect & 0xFF;
			int param  = note.parameter & 0xFF;
			if( effect == 0xE ) {
				effect = 0x10 | ( param >> 4 );
				param &= 0xF;
			}
			if( effect == 0 && param > 0 ) effect = 0xE;
			channel.row( note.key, note.instrument, effect, param );
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
						if( param < 32 )
							tick = speed = param;
						else
							tempo = param;
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
