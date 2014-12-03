package micromod.compiler;

public class Waveform implements Element {
	private Instrument parent;
	private WaveFile sibling;
	private Octave child = new Octave( this );
	private byte[] envelope = new byte[ 512 ];
	private int octave, detune, chorus, x0, y0;
	private boolean spectral, noise, pwm;

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
		spectral = noise = pwm = false;
		if( "Sawtooth".equals( value ) ) {
			setEnvelopePoint(   0, -128 );
			setEnvelopePoint( 511,  127 );
		} else if( "Square".equals( value ) ) {
			setEnvelopePoint(   0, -128 );
			setEnvelopePoint( 255, -128 );
			setEnvelopePoint( 256,  127 );
			setEnvelopePoint( 511,  127 );
		} else if( "Envelope".equals( value ) ) {
			setEnvelopePoint(   0, 0 );
			setEnvelopePoint( 511, 0 );
			setEnvelopePoint(   0, 0 );			
		} else if( "Sine".equals( value ) || "Harmonics".equals( value ) ) {
			spectral = true;
			setEnvelopePoint(   0,    0 );
			setEnvelopePoint(   1, -128 );
			setEnvelopePoint(   2,    0 );
			setEnvelopePoint( 511,    0 );
			setEnvelopePoint(   0,    0 );
		} else if( "Noise".equals( value ) ) {
			spectral = noise = true;
			setEnvelopePoint( 0, 0 );
			setEnvelopePoint( 1, 1 );
			setEnvelopePoint( 256, 1 );
			setEnvelopePoint( 511, 0 );
			setEnvelopePoint( 0, 0 );
		} else {
			throw new IllegalArgumentException( "Invalid waveform type: " + value );
		}
		setOctave( 0 );
		setDetune( 0 );
		setChorus( 1, false );
	}

	public void end() {
		if( noise ) {
			parent.setAudioData( new AudioData( noise( envelope ), 8363 ) );
		} else {
			int cycles = 2;
			if( chorus > 1 ) {
				cycles = chorus;
			} else if( detune != 0 ) {
				cycles = 128;
			}
			byte[] waveform = spectral ? harmonics( envelope ) : envelope;
			if( chorus > 1 && pwm ) {
				waveform = genPulseMod( waveform, cycles / 2, octave, detune );
			} else {
				waveform = genPhaseMod( waveform, cycles, octave, detune, chorus > 1 );
			}
			AudioData audioData = new AudioData( waveform, 512 * 262 );
			if( octave > -4 ) {
				audioData = audioData.resample( audioData.getSamplingRate() >> ( octave + 4 ), 0, true );
			}
			parent.setAudioData( audioData );
		}
		parent.setLoopStart( 0 );
		parent.setLoopLength( parent.getAudioData().getLength() );
	}

	public void setOctave( int octave ) {
		if( octave < -4 || octave > 4 ) {
			throw new IllegalArgumentException( "Invalid octave (-4 to 4): " + octave );
		}
		this.octave = octave;
	}
	
	public void setDetune( int pitch ) {
		if( pitch < -192 || pitch > 192 ) {
			throw new IllegalArgumentException( "Invalid detune (-192 to 192): " + pitch );
		}
		this.detune = pitch;
	}

	public void setChorus( int cycles, boolean pwm ) {
		if( cycles < 1 || cycles > 1024 ) {
			throw new IllegalArgumentException( "Invalid chorus length (1 to 1024): " + cycles );
		}
		this.chorus = cycles;
		this.pwm = pwm;
	}

	/* Set the envelope from x0 to x1 (0 to 511) by interpolating y0 to y1 (-128 to 127).
	   The values of x0 and y0 are taken from the previously set envelope point (unless x1 is 0). */
	public void setEnvelopePoint( int x1, int y1 ) {
		if( x1 < 0 || x1 > 511 ) {
			throw new IllegalArgumentException( "Invalid envelope index (0 to 511): " + x1 );
		}
		if( y1 < -128 || y1 > 127 ) {
			throw new IllegalArgumentException( "Invalid envelope amplitude (-128 to 127): " + y1 );
		}
		if( x1 == 0 ) {
			envelope[ 0 ] = ( byte ) y1;
		} else {
			if( x1 <= x0 ) {
				throw new IllegalArgumentException( "Invalid envelope index (must increase): " + x1 );
			}
			for( int x = 0, dx = x1 - x0, dy = y1 - y0; x <= dx; x++ ) {
				envelope[ x0 + x ] = ( byte ) ( dy * x / dx + y0 );
			}
		}
		x0 = x1;
		y0 = y1;
	}

	/* Generate the specified number of cycles of the specified 512-byte waveform.
	   at the specified octave with optional 2-oscillator detune and phase-modulation chorus.
	   The cycles parameter determines the cycle length of the chorus and accuracy of the detune. */
	public static byte[] genPulseMod( byte[] waveform, int cycles, int octave, int detune, boolean chorus ) {
		byte[] buf = new byte[ 512 * cycles ];
		int cycles2 = Math.round( ( float ) ( cycles * Math.pow( 2, detune / 96d ) ) );
		for( int cycle = 0; cycle < cycles; cycle++ ) {
			int mod = chorus ? 256 + Math.round( ( float ) ( Math.cos( 2 * Math.PI * cycle / cycles ) * 256 ) ) : 0;
			for( int ph1 = 0; ph1 < 512; ph1++ ) {
				int ph2 = ( cycle * 512 + ph1 ) * cycles2 / cycles;
				buf[ cycle * 512 + ph1 ] = ( byte ) ( ( waveform[ ph1 ] + waveform[ ( ph2 + mod ) & 0x1FF ] ) / 2 );
			}
		}
		return buf;
	}

	/* Generate the specified number of 1024-byte cycles by frequency-modulating the specified 512-byte waveform
	   and mixing it with the non-modulated waveform detuned by the specified number of 96ths of an octave.
	   The cycles parameter determines the cycle length of the modulation effect and accuracy of the detune. */
	public static byte[] genPhaseMod( byte[] waveform, int cycles, int octave, int detune ) {
		byte[] buf = new byte[ cycles * 1024 ];
		int cycles2 = Math.round( ( float ) ( cycles * Math.pow( 2, detune / 96d ) ) );
		for( int cycle = 0; cycle < cycles; cycle++ ) {
			int pw1 = Math.round( 512 * ( float ) Math.pow( 2, ( Math.cos( 2 * Math.PI * cycle / cycles ) - 1 ) / 2 ) );
			for( int ph1 = 0; ph1 < pw1; ph1++ ) {
				int ph2 = ( cycle * 1024 + ph1 ) * cycles2 / cycles;
				buf[ cycle * 1024 + ph1 ] = ( byte ) ( ( waveform[ ph1 * 512 / pw1 ] + waveform[ ph2 & 0x1FF ] ) / 2 );
			}
			for( int ph1 = 0, pw2 = ( 1024 - pw1 ); ph1 < pw2; ph1++ ) {
				int ph2 = ( cycle * 1024 + ph1 + pw1 ) * cycles2 / cycles;
				buf[ cycle * 1024 + ph1 + pw1 ] = ( byte ) ( ( waveform[ ph1 * 512 / pw2 ] + waveform[ ph2 & 0x1FF ] ) / 2 );
			}
		}
		return buf;
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

	/* Generate a 512-byte sine table. */
	public static byte[] sine() {
		byte[] sine = new byte[ 512 ];
		for( int idx = 0; idx < 512; idx++ ) {
			sine[ idx ] = ( byte ) Math.round( Math.sin( Math.PI * idx / 256 ) * 127 );
		}
		return sine;
	}
}
