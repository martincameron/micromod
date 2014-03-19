package projacker;

public class Waveform implements Element {
	private Instrument parent;
	private WaveFile sibling;

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
		return null;
	}
	
	public void begin( String value ) {
		System.out.println( getToken() + ": " + value );
		byte[] waveform = new byte[ 32 ];
		if( "Sawtooth".equals( value.toString() ) ) {
			for( int idx = 0; idx < 32; idx++ ) {
				waveform[ idx ] = ( byte ) ( ( idx << 3 ) - 128 );
			}
		} else if( "Square".equals( value.toString() ) ) {
			for( int idx = 0; idx < 32; idx++ ) {
				waveform[ idx ] = ( byte ) ( ( ( idx & 0x10 ) >> 4 ) * 255 - 128 );
			}
		} else {
			throw new IllegalArgumentException( "Invalid waveform type: " + value );
		}
		parent.setAudioData( new AudioData( waveform, 8363 ) );
		parent.setLoopStart( 0 );
		parent.setLoopLength( waveform.length );
	}
	
	public void end() {
	}
}
