package micromod.compiler;

public class WaveFile implements Element {
	private Instrument parent;
	private LoopStart sibling;
	private Crop child = new Crop( this );
	private int offset, count, divisions, gain, pitch;

	public WaveFile( Instrument parent ) {
		this.parent = parent;
		sibling = new LoopStart( parent );
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
		try {
			/* Get the left/mono channel from the wav file. */
			java.io.InputStream inputStream = parent.getInputStream( value.toString() );
			parent.setAudioData( new AudioData( inputStream, 0 ) );
			setCrop( 0, 0, 0 );
			setGain( 64 );
			setPitch( 0 );
		} catch( java.io.IOException e ) {
			throw new IllegalArgumentException( e );
		}
	}
	
	public void end() {
		AudioData audioData = parent.getAudioData();
		if( count > 0 ) {
			int length = 1;
			if( divisions > 1 ) {
				length = audioData.getLength() / divisions;
			}
			audioData = audioData.crop( offset * length, count * length );
		}
		if( gain != 64 ) {
			audioData = audioData.scale( gain );
		}
		if( pitch != 0 ) {
			audioData = audioData.resample( audioData.getSamplingRate(), pitch );
		}
		parent.setAudioData( audioData );
	}

	public String description() {
		return "\"FileName\" (The relative path of the Wav file.)";
	}

	public void setCrop( int offset, int count, int divisions ) {
		this.offset = offset;
		this.count = count;
		this.divisions = divisions;
	}

	public void setGain( int gain ) {
		this.gain = gain;
	}
	
	public void setPitch( int pitch ) {
		this.pitch = pitch;
	}
}
