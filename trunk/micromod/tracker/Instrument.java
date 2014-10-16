
package micromod.tracker;

public class Instrument implements Element {
	private micromod.Instrument instrument;
	private int instrumentIndex, loopStart, loopLength;
	private AudioData audioData;
	private Module parent;
	private Macro sibling;
	private Name child = new Name( this );
	
	public Instrument( Module parent ) {
		this.parent = parent;
		sibling = new Macro( parent );
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
		instrumentIndex = Parser.parseInteger( value );
		instrument = parent.getInstrument( instrumentIndex );
		loopStart = loopLength = 0;
		audioData = null;
	}
	
	public void end() {
		if( audioData != null ) {
			instrument.setSampleData( audioData.quantize(), loopStart, loopLength );
		}
		System.out.println( "Instrument " + instrumentIndex +
			": Volume " + instrument.getVolume() +
			", FineTune " + instrument.getFineTune() +
			", LoopStart " + instrument.getLoopStart() +
			", SampleEnd " + ( instrument.getLoopStart() + instrument.getLoopLength() ) );
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
	
	public java.io.InputStream getInputStream( String path ) throws java.io.IOException {
		return parent.getInputStream( path );
	}
}
