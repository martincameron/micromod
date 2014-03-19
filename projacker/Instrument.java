
package projacker;

public class Instrument implements Element {
	private micromod.Instrument instrument;
	private int loopStart, loopLength;
	private AudioData audioData;
	private Module parent;
	private Pattern sibling;
	private Name child = new Name( this );
	
	public Instrument( Module parent ) {
		this.parent = parent;
		sibling = new Pattern( parent );
	}
	
	public String getToken() {
		return "Instrument";
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
		instrument = parent.getInstrument( Parser.parseInteger( value ) );
		loopStart = loopLength = 0;
		audioData = null;
	}
	
	public void end() {
		if( audioData != null ) {
			instrument.setSampleData( audioData.quantize(), loopStart, loopLength );
		}
		System.out.println( "Instrument end." );
	}
	
	public void setName( String name ) {
		instrument.setName( name );
	}
	
	public void setVolume( int volume ) {
		instrument.setVolume( volume );
	}
	
	public void setFineTune( int fineTune ) {
		instrument.setFineTune( fineTune );
	}
	
	public AudioData getAudioData() {
		return audioData;
	}
	
	public void setAudioData( AudioData audioData ) {
		this.audioData = audioData;
	}
	
	public void setLoopStart( int loopStart ) {
		this.loopStart = loopStart;
	}

	public void setLoopLength( int loopLength ) {
		this.loopLength = loopLength;
	}
}
