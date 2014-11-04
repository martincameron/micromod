package micromod.compiler;

public class Waveform implements Element {
	private Instrument parent;
	private WaveFile sibling;
	private Octave child = new Octave( this );
	private byte[] waveform = new byte[ 512 ];
	private int octave, detune, chorus, x0, y0;

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
		if( "Sine".equals( value ) ) {
			for( int idx = 0; idx < 512; idx++ ) {
				waveform[ idx ] = ( byte ) Math.round( Math.sin( Math.PI * idx / 256 ) * 127 );
			}
		} else if( "Sawtooth".equals( value ) ) {
			setEnvelopePoint(   0, -128 );
			setEnvelopePoint( 511,  127 );
		} else if( "Square".equals( value ) ) {
			setEnvelopePoint(   0, -128 );
			setEnvelopePoint( 255, -128 );
			setEnvelopePoint( 256,  127 );
			setEnvelopePoint( 511,  127 );
		} else if( "Envelope".equals( value ) ) {
			setEnvelopePoint(   0,    0 );
			for( int idx = 0; idx < 512; idx++ ) {
				waveform[ idx ] = 0;
			}
		} else {
			throw new IllegalArgumentException( "Invalid waveform type: " + value );
		}
		setOctave( 0 );
		setDetune( 0 );
		setChorus( 1 );
	}
	
	public void end() {
		int cycles = 1;
		if( chorus > 1 ) {
			cycles = chorus;
		} else if( detune != 0 ) {
			cycles = 128;
		}
		AudioData audioData = generate( waveform, cycles, octave, detune, chorus > 1 );
		parent.setAudioData( audioData );
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

	public void setChorus( int cycles ) {
		if( cycles < 1 || cycles > 1024 ) {
			throw new IllegalArgumentException( "Invalid chorus length (1 to 1024): " + cycles );
		}
		this.chorus = cycles;
	}

	/* Set the waveform from x0 to x1 (0 to 511) by interpolating y0 to y1 (-128 to 127).
	   The values of x0 and y0 are taken from the previously set envelope point (unless x1 is 0). */
	public void setEnvelopePoint( int x1, int y1 ) {
		if( x1 < 0 || x1 > 511 ) {
			throw new IllegalArgumentException( "Invalid envelope index (0 to 511): " + x1 );
		}
		if( y1 < -128 || y1 > 127 ) {
			throw new IllegalArgumentException( "Invalid envelope amplitude (-128 to 127): " + y1 );
		}
		if( x1 == 0 ) {
			waveform[ 0 ] = ( byte ) y1;
		} else {
			if( x1 <= x0 ) {
				throw new IllegalArgumentException( "Invalid envelope index (must increase): " + x1 );
			}
			for( int x = x0; x <= x1; x++ ) {
				waveform[ x ] = ( byte ) ( ( y1 - y0 ) * x / ( x1 - x0 ) + y0 );
			}
		}
		x0 = x1;
		y0 = y1;
	}

	/* Generate the specified number of cycles of the specified 512-byte waveform.
	   at the specified octave with optional 2-oscillator detune and phase-modulation chorus.
	   The cycles parameter determines the cycle length of the chorus and accuracy of the detune. */
	public static AudioData generate( byte[] waveform, int cycles, int octave, int detune, boolean chorus ) {
		byte[] buf = new byte[ 512 * cycles ];
		int cycles2 = Math.round( ( float ) ( cycles * Math.pow( 2, detune / 96d ) ) );
		for( int cycle = 0; cycle < cycles; cycle++ ) {
			int mod = chorus ? 256 + Math.round( ( float ) ( Math.cos( 2 * Math.PI * cycle / cycles ) * 256 ) ) : 0;
			for( int ph1 = 0; ph1 < 512; ph1++ ) {
				int ph2 = ( cycle * 512 + ph1 ) * cycles2 / cycles;
				buf[ cycle * 512 + ph1 ] = ( byte ) ( ( waveform[ ph1 ] + waveform[ ( ph2 + mod ) & 0x1FF ] ) / 2 );
			}
		}
		return new AudioData( buf, 512 * 262 ).resample( ( 512 * 262 ) >> ( octave + 4 ), 0, true );
	}
}
