package projacker;

public class WaveFile implements Element {
	private Instrument parent;
	private Gain sibling;

	public WaveFile( Instrument parent ) {
		this.parent = parent;
		sibling = new Gain( parent );
	}
	
	public String getToken() {
		return "WaveFile";
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
		try {
			// Get the left/mono channel from the wav file.
			parent.setAudioData( new AudioData( new java.io.FileInputStream( value.toString() ), 0 ) );
		} catch( java.io.IOException e ) {
			throw new IllegalArgumentException( e );
		}
	}
	
	public void end() {
	}
}
