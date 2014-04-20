package projacker;

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
		System.out.println( getToken() + ": " + value );
		if( "Sawtooth".equals( value ) ) {
			squareWave = false;
		} else if( "Square".equals( value.toString() ) ) {
			squareWave = true;
		} else {
			throw new IllegalArgumentException( "Invalid waveform type: " + value );
		}
		setOctave( 3 );
		setNumCycles( 1 );
	}
	
	public void end() {
		AudioData audioData = generate( squareWave, numCycles );
		if( octave > 0 ) {
			audioData = audioData.resample( audioData.getSamplingRate() >> octave );
		}
		parent.setAudioData( audioData );
		parent.setLoopStart( 0 );
		parent.setLoopLength( parent.getAudioData().getSampleData().length );
	}
	
	public void setOctave( int octave ) {
		this.octave = octave;
	}
	
	public void setNumCycles( int cycles ) {
		if( cycles < 1 ) {
			throw new IllegalArgumentException( "Invalid number of cycles: " + cycles );
		}
		this.numCycles = cycles;
	}
	
	/* Generate a sawtooth or square waveform with phase-modulation chorus if cycles is greater than 1. */
	public static AudioData generate( boolean square, int cycles ) {
		byte[] buf = new byte[ 256 * cycles ];
		for( int cycle = 0; cycle < cycles; cycle++ ) {
			int phase = 128 + Math.round( ( float ) ( Math.cos( 2 * Math.PI * cycle / cycles ) * 128 ) );
			if( square ) {
				for( int idx = 0; idx < 256; idx++ ) {
					buf[ cycle * 256 + idx ] = ( byte ) ( ( ( idx & 0x80 ) + ( ( idx + phase ) & 0x80 ) ) * 255 / 256 - 128 );
				}
			} else {
				for( int idx = 0; idx < 256; idx++ ) {
					buf[ cycle * 256 + idx ] = ( byte ) ( ( ( idx % 256 ) + ( idx + phase ) % 256 ) / 2 - 128 );
				}
			}
		}
		return new AudioData( buf, 256 * 262 );
	}
}
