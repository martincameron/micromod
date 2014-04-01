package projacker;

public class Pitch implements Element {
	private Instrument parent;
	private LoopStart sibling;

	public Pitch( Instrument parent ) {
		this.parent = parent;
		sibling = new LoopStart( parent );
	}
	
	public String getToken() {
		return "Pitch";
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
		int pitch = Parser.parseInteger( value );
		AudioData audioData = parent.getAudioData();
		double rate = audioData.getSamplingRate() * Math.pow( 2, pitch / -96.0 );
		parent.setAudioData( audioData.resample( ( int ) Math.round( rate ) ) );
	}
	
	public void end() {
	}
}
