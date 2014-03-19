package projacker;

public class WaveFile implements Element {
	private Instrument parent;
	private LoopStart sibling;
	private Gain child;

	public WaveFile( Instrument parent ) {
		this.parent = parent;
		sibling = new LoopStart( parent );
		child = new Gain( this );
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
		return child;
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
	
	public void setGain( int gain ) {
		parent.setAudioData( parent.getAudioData().scale( gain ) );
	}
	
	public void setPitch( int pitch ) {
		AudioData audioData = parent.getAudioData();
		double rate = audioData.getSamplingRate() * Math.pow( 2, pitch / -96.0 );
		parent.setAudioData( audioData.resample( ( int ) Math.round( rate ) ) );
	}
}
