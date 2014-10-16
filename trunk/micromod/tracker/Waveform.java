package micromod.tracker;

public class Waveform implements Element {
	private Instrument parent;
	private WaveFile sibling;
	private Octave child = new Octave( this );
	private boolean squareWave;
	private int octave, numCycles;

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
		setNumCycles( 1 );
	}
	
	public void end() {
		AudioData audioData = generate( squareWave, numCycles, octave );
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
	
	public void setNumCycles( int cycles ) {
		if( cycles < 1 ) {
			throw new IllegalArgumentException( "Invalid number of cycles: " + cycles );
		}
		this.numCycles = cycles;
	}
	
	/* Generate a sawtooth or square waveform with phase-modulation chorus if cycles is greater than 1. */
	public static AudioData generate( boolean square, int cycles, int octave ) {
		byte[] buf = new byte[ 512 * cycles ];
		for( int cycle = 0; cycle < cycles; cycle++ ) {
			int phase = 256 + Math.round( ( float ) ( Math.cos( 2 * Math.PI * cycle / cycles ) * 256 ) );
			if( square ) {
				for( int idx = 0; idx < 512; idx++ ) {
					buf[ cycle * 512 + idx ] = ( byte ) ( ( ( idx & 0x100 ) + ( ( idx + phase ) & 0x100 ) ) * 255 / 512 - 128 );
				}
			} else {
				for( int idx = 0; idx < 512; idx++ ) {
					buf[ cycle * 512 + idx ] = ( byte ) ( ( ( idx % 512 ) + ( idx + phase ) % 512 ) / 4 - 128 );
				}
			}
		}
		return new AudioData( buf, 512 * 262 ).resample( ( 512 * 262 ) >> ( octave + 4 ), 0, true );
	}
}
