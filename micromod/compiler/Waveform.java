package micromod.compiler;

public class Waveform implements Element {
	private Instrument parent;
	private WaveFile sibling;
	private Octave child = new Octave( this );
	private byte[] envelope = new byte[ 512 ];
	private int octave, cycles, modRate, lfoRate, detune, mix, x0, y0;
	private boolean spectral, noise;
	private AudioData audioData;

	public Waveform() {
	}

	public Waveform( Instrument parent ) {
		this.parent = parent;
		sibling = new WaveFile( parent );
	}
	
	public String getToken() {
		return "Waveform";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return sibling;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		spectral = noise = false;
		if( "Sawtooth".equals( value ) ) {
			setEnvelopePoint(   0, -128 );
			setEnvelopePoint( 511,  127 );
		} else if( "Square".equals( value ) ) {
			setEnvelopePoint(   0, -128 );
			setEnvelopePoint( 255, -128 );
			setEnvelopePoint( 256,  127 );
			setEnvelopePoint( 511,  127 );
		} else if( "Sine".equals( value ) ) {
			spectral = true;
			setEnvelopePoint(   0,    0 );
			setEnvelopePoint(   1, -128 );
			setEnvelopePoint(   2,    0 );
			setEnvelopePoint( 511,    0 );
		} else if( "Noise".equals( value ) ) {
			spectral = noise = true;
			setEnvelopePoint( 0, 0 );
			setEnvelopePoint( 1, 1 );
			setEnvelopePoint( 256, 1 );
			setEnvelopePoint( 257, 0 );
			setEnvelopePoint( 511, 0 );
		} else {
			throw new IllegalArgumentException( "Invalid waveform type: " + value );
		}
		setOctave( 0 );
		setModulation( 1, 0, 0, 0, 128 );
	}

	public void end() {
		if( noise ) {
			setAudioData( new AudioData( noise( envelope ), 512 * 262 ) );
		} else {
			byte[] modulator = cosine();
			byte[] carrier = spectral ? harmonics( envelope ) : envelope;
			byte[] waveform = new byte[ cycles * 512 ];
			fm( carrier, modulator, modulator, cycles, modRate, lfoRate, detune, mix, waveform );
			if( octave > -4 ) {
				byte[] outBuf = new byte[ waveform.length + 1024 ];
				for( int idx = 0; idx < outBuf.length; idx++ ) {
					outBuf[ idx ] = waveform[ idx % waveform.length ];
				}
				AudioData audioData = new AudioData( outBuf, 512 * 262 );
				audioData = audioData.resample( audioData.getSamplingRate() >> ( octave + 4 ) );
				audioData = audioData.crop( 512 >> ( octave + 4 ), audioData.getLength() - ( 1024 >> ( octave + 4 ) ) );
				setAudioData( audioData );
			} else {
				setAudioData( new AudioData( waveform, 512 * 262 ) );
			}
		}
	}

	public String description() {
		return "\"Type\" ('Sawtooth', 'Square', 'Sine', or 'Noise'.)";
	}

	public AudioData getAudioData() {
		return audioData;
	}

	public void setAudioData( AudioData audioData ) {
		this.audioData = audioData;
		if( parent != null ) {
			parent.setAudioData( audioData );
			parent.setLoopStart( 0 );
			parent.setLoopLength( audioData.getLength() );
		}
	}

	public void setOctave( int octave ) {
		if( octave < -4 || octave > 4 ) {
			throw new IllegalArgumentException( "Invalid octave (-4 to 4): " + octave );
		}
		this.octave = octave;
	}

	public void setModulation( int cycles, int modRate, int lfoRate, int detune, int mix ) {
		if( cycles < 1 || cycles > 1024 ) {
			throw new IllegalArgumentException( "Invalid number of cycles (1 to 1024): " + cycles );
		}
		this.cycles = cycles;
		if( modRate < 0 || modRate > 1024 ) {
			throw new IllegalArgumentException( "Invalid modulation rate (0 to 1024): " + modRate );
		}
		this.modRate = modRate;
		if( lfoRate < 0 || lfoRate > 1024 ) {
			throw new IllegalArgumentException( "Invalid LFO rate (0 to 1024): " + lfoRate );
		}
		this.lfoRate = lfoRate;
		if( detune < -192 || detune > 192 ) {
			throw new IllegalArgumentException( "Invalid detune pitch (-192 to 192): " + detune );
		}
		this.detune = detune;
		if( mix < 0 || mix > 256 ) {
			throw new IllegalArgumentException( "Invalid mix parameter (0 to 256): " + mix );
		}
		this.mix = mix;
	}

	/* Set the envelope from x0 to x1 (0 to 511) by interpolating y0 to y1 (-128 to 127).
	   The values of x0 and y0 are taken from the previously set envelope point, unless
	   x1 is less than or equal to x0, which sets a single point without interpolation.*/
	public void setEnvelopePoint( int x1, int y1 ) {
		if( x1 < 0 || x1 > 511 ) {
			throw new IllegalArgumentException( "Invalid envelope index (0 to 511): " + x1 );
		}
		if( y1 < -128 || y1 > 127 ) {
			throw new IllegalArgumentException( "Invalid envelope amplitude (-128 to 127): " + y1 );
		}
		if( x1 <= x0 ) {
			envelope[ x1 ] = ( byte ) y1;
		} else {
			for( int x = 0, dx = x1 - x0, dy = y1 - y0; x <= dx; x++ ) {
				envelope[ x0 + x ] = ( byte ) ( dy * x / dx + y0 );
			}
		}
		x0 = x1;
		y0 = y1;
	}

	/* Generate a 512-byte periodic waveform from the specified 256-harmonic spectrum. */
	public static byte[] harmonics( byte[] spectrum ) {
		int[] window = new int[ 512 ];
		for( int idx = 0; idx < 512; idx++ ) {
			window[ idx ] = 256;
		}
		byte[] output = new byte[ 512 ];
		additive( sine(), spectrum, new int[ 512 ], window, output, 0, 0x1FF );
		return output;
	}

	/* Generate a 65536-byte noise waveform from the specified 256-harmonic spectrum. */
	public static byte[] noise( byte[] spectrum ) {
		byte[] sine = sine();
		int[] window = new int[ 512 ];
		for( int idx = 0; idx < 256; idx++ ) {
			window[ idx ] = idx;
			window[ idx + 256 ] = 256 - idx;
		}
		int random = 0;
		int[] phase = new int[ 512 ];
		byte[] output = new byte[ 65536 ];
		for( int offset = 0; offset < 65536; offset += 256 ) {
			for( int idx = 0; idx < 512; idx++ ) {
				random = random * 65 + 17;
				phase[ idx ] = ( random >> 24 ) & 0x1FF;
			}
			additive( sine, spectrum, phase, window, output, offset, 0xFFFF );
		}
		return output;
	}

	/* Simple additive synthesis. */
	public static void additive( byte[] sine, byte[] spectrum, int[] phase, int[] window, byte[] output, int offset, int mask ) {
		for( int idx = 0; idx < 512; idx++ ) {
			int amp = 0;
			for( int partial = 1; partial <= 256; partial++ ) {
				amp = amp + sine[ ( phase[ partial ] + idx * partial ) & 0x1FF ] * spectrum[ partial ];
			}
			int outIdx = ( offset + idx ) & mask;
			amp = ( output[ outIdx ] * 32768 + amp * window[ idx ] ) / 32768;
			if( amp < -128 ) amp = -128;
			if( amp >  127 ) amp =  127;
			output[ outIdx ] = ( byte ) amp;
		}
	}

	/* Simple 3-operator FM synthesis. */
	public static void fm( byte[] carrier, byte[] modulator, byte[] lfo, int cycles, int modRate, int lfoRate, int detune, int mix, byte[] output ) {
		int cycles2 = Math.round( ( float ) ( cycles * Math.pow( 2, detune / 96d ) ) );
		for( int idx = 0, end = cycles * 512; idx < end; idx++ ) {
			int out1 = carrier[ idx & 0x1FF ];
			int out2 = carrier[ ( idx * cycles2 / cycles + ( 128 + modulator[ ( idx * modRate / cycles ) & 0x1FF ] ) * ( 128 + lfo[ ( idx * lfoRate / cycles ) & 0x1FF ] ) / 128 ) & 0x1FF ];
			output[ idx ] = ( byte ) ( ( out1 * mix + out2 * ( 256 - mix ) ) / 256 );
		}
	}

	/* Generate a 512-byte sine table. */
	public static byte[] sine() {
		byte[] sine = new byte[ 512 ];
		for( int idx = 0; idx < 512; idx++ ) {
			sine[ idx ] = ( byte ) Math.round( Math.sin( Math.PI * idx / 256 ) * 127 );
		}
		return sine;
	}

	public static byte[] cosine() {
		byte[] cosine = new byte[ 512 ];
		for( int idx = 0; idx < 512; idx++ ) {
			cosine[ idx ] = ( byte ) Math.round( Math.cos( Math.PI * idx / 256 ) * 127 );
		}
		return cosine;
	}

	public static byte[] sawtooth() {
		byte[] saw = new byte[ 512 ];
		for( int idx = 0; idx < 512; idx++ ) {
			saw[ idx ] = ( byte ) ( idx / 2 - 128 );
		}
		return saw;
	}

	public static byte[] triangle() {
		byte[] tri = new byte[ 512 ];
		for( int idx = 0; idx < 256; idx++ ) {
			tri[ idx ] = ( byte ) ( idx - 128 );
			tri[ idx + 256 ] = ( byte ) ( 127 - idx );
		}
		return tri;
	}

	public static void main( String[] args ) throws java.io.IOException {
		int idx = 0;
		String patch = "Waveform Sawtooth Octave -2 Chorus 1024,0", waveFile = null;
		while( idx < args.length ) {
			String arg = args[ idx++ ];
			if( "-patch".equals( arg ) ){
				patch = args[ idx++ ];
			} else if( "-wav".equals( arg ) ) {
				waveFile = args[ idx++ ];
			}
		}
		Waveform waveform = new Waveform();
		if( waveFile != null ) {
			Parser.parse( new java.io.StringReader( patch ), waveform );
			java.io.OutputStream outputStream = new java.io.FileOutputStream( waveFile );
			try {
				AudioData audioData = waveform.getAudioData();
				audioData.setSamplingRate( 512 * 262 / 4 );
				audioData.writeWav( outputStream, false );
			} finally {
				outputStream.close();
			}
		} else {
			System.err.println( "Usage: " + Waveform.class.getName() +
				"\n      -patch \"" + patch + "\" -wav output.wav" );
			System.err.println( "Patch syntax: " + Parser.syntax( waveform ) );
		}
	}
}
