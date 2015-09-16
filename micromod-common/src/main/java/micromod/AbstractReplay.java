package micromod;

public abstract class AbstractReplay<MODULE extends AbstractModule<PATTERN, INSTRUMENT>, PATTERN extends AbstractPattern, INSTRUMENT, CHANNEL extends AbstractChannel> {
	protected final MODULE module;
	protected final byte[][] playCount;

	protected int sampleRate;
	protected int row, seqPos;
	protected int speed, tempo;
	protected int tick;
	private ChannelInterpolation interpolation;
	protected final int[] rampBuf = new int[ 128 ];;

	public AbstractReplay( MODULE module ) {
		this.module = module;
		this.playCount = new byte[ module.getSequenceLength() ][];
	}

	/**
	 * Return the sampling rate of playback.
	 **/
	public int getSampleRate() {
		return sampleRate;
	}

	/**
	 * Set the sampling rate of playback.
	 * <p>Use with Module.c2Rate to adjust the tempo of playback.</p>
	 * <p>To play at half speed, multiply both the samplingRate and Module.c2Rate by 2.</p>
	 **/
	public void setSampleRate( int rate ) {
		if( rate < 8000 || rate > 128000 ) {
			throw new IllegalArgumentException( "Unsupported sampling rate!" );
		}
		sampleRate = rate;
	}

	/**
	 * Return the length of the buffer required by getAudio().
	 */
	public int getMixBufferLength() {
		return (calculateTickLen(32, 128000) + 65) * 4;
	}

	/**
	 * Get the current row position.
	 */
	public int getRow() {
		return row;
	}

	/**
	 * Get the current pattern position in the sequence.
	 */
	public int getSequencePos() {
		return seqPos;
	}

	/**
	 * Set the pattern in the sequence to play. The tempo is reset to the default.
	 */
	public abstract void setSequencePos(int pos);

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

	private int calculateTickLen( int tempo, int samplingRate ) {
		return ( samplingRate * 5 ) / ( tempo * 2 );
	}

	/* Seek to the specified position and row in the sequence. */
	public void seekSequencePos( int sequencePos, int sequenceRow ) {
		setSequencePos( 0 );
		if( sequencePos < 0 || sequencePos >= module.getSequenceLength() )
			sequencePos = 0;
		if( sequenceRow >= module.getPattern( module.getSequenceEntry( sequencePos ) ).getNumRows() )
			sequenceRow = 0;
		while( seqPos < sequencePos || row < sequenceRow ) {
			int tickLen = calculateTickLen( tempo, sampleRate );
			for(int idx = 0; idx < getNumChannels(); idx++ )
				getChannel( idx ).updateSampleIdx( tickLen * 2, sampleRate * 2 );
			if( tick() ) {
				/* Song end reached. */
				setSequencePos( sequencePos );
				return;
			}
		}
	}

	/* Seek to approximately the specified sample position.
	   The actual sample position reached is returned. */
	public int seek( int samplePos ) {
		setSequencePos( 0 );
		int currentPos = 0;
		int tickLen = calculateTickLen( tempo, sampleRate );
		while( ( samplePos - currentPos ) >= tickLen ) {
			for(int idx = 0; idx < getNumChannels(); idx++ )
				getChannel( idx ).updateSampleIdx( tickLen * 2, sampleRate * 2 );
			currentPos += tickLen;
			tick();
			tickLen = calculateTickLen( tempo, sampleRate );
		}
		return currentPos;
	}


	/* Generate audio.
	   The number of samples placed into outputBuf is returned.
	   The output buffer length must be at least that returned by getMixBufferLength().
	   A "sample" is a pair of 16-bit integer amplitudes, one for each of the stereo channels. */
	public int getAudio( int[] outputBuf ) {
		int tickLen = calculateTickLen( tempo, sampleRate );
		/* Clear output buffer. */
		for( int idx = 0, end = ( tickLen + 65 ) * 4; idx < end; idx++ )
			outputBuf[ idx ] = 0;
		/* Resample. */
		for( int chanIdx = 0; chanIdx < getNumChannels(); chanIdx++ ) {
			CHANNEL chan = getChannel( chanIdx );
			chan.resample( outputBuf, 0, ( tickLen + 65 ) * 2, sampleRate * 2, getInterpolation() );
			chan.updateSampleIdx( tickLen * 2, sampleRate * 2 );
		}
		downsample( outputBuf, tickLen + 64 );
		volumeRamp( outputBuf, tickLen );
		tick();
		return tickLen;
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

	protected abstract int getNumChannels();

	protected boolean tick() {
		if( --tick <= 0 ) {
			tick = speed;
			row();
		} else {
			for(int idx = 0; idx < getNumChannels(); idx++ ) getChannel( idx ).tick();
		}
		return playCount[ seqPos ][ row ] > 1;
	}

	protected abstract void row();
	protected abstract CHANNEL getChannel( int idx );

	public ChannelInterpolation getInterpolation() {
		return interpolation;
	}

	/** Set the resampling quality to one of
	   ChannelInterpolation.NEAREST, ChannelInterpolation.LINEAR, or ChannelInterpolation.SINC. */
	public void setInterpolation( ChannelInterpolation interpolation ) {
		this.interpolation = interpolation;
	}
}
