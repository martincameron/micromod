package micromod.compiler;

public class Waveform implements Element {
	private Instrument parent;
	private WaveFile sibling;
	private Octave child = new Octave( this );
	private boolean squareWave;
	private int octave, detune, chorus;

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
		if( "Sawtooth".equals( value ) ) {
			squareWave = false;
		} else if( "Square".equals( value.toString() ) ) {
			squareWave = true;
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
		AudioData audioData = generate( squareWave, cycles, octave, detune, chorus > 1 );
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

	/* Generate the specified number of cycles of a sawtooth or square waveform
	   at the specified octave with optional 2-oscillator detune and phase-modulation chorus.
	   The cycles parameter determines the cycle length of the chorus and accuracy of the detune. */
	public static AudioData generate( boolean square, int cycles, int octave, int detune, boolean chorus ) {
		byte[] buf = new byte[ 512 * cycles ];
		int cycles2 = Math.round( ( float ) ( cycles * Math.pow( 2, detune / 96d ) ) );
		for( int cycle = 0; cycle < cycles; cycle++ ) {
			int mod = chorus ? 256 + Math.round( ( float ) ( Math.cos( 2 * Math.PI * cycle / cycles ) * 256 ) ) : 0;
			for( int ph1 = 0; ph1 < 512; ph1++ ) {
				int ph2 = ( cycle * 512 + ph1 ) * cycles2 / cycles;
				if( square ) {
					buf[ cycle * 512 + ph1 ] = ( byte ) ( ( ( ph1 & 0x100 ) + ( ( ph2 + mod ) & 0x100 ) ) * 255 / 512 - 128 );
				} else {
					buf[ cycle * 512 + ph1 ] = ( byte ) ( ( ( ph1 % 512 ) + ( ph2 + mod ) % 512 ) / 4 - 128 );
				}
			}
		}
		return new AudioData( buf, 512 * 262 ).resample( ( 512 * 262 ) >> ( octave + 4 ), 0, true );
	}
}
