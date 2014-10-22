package micromod.tracker;

public class WaveFile implements Element {
	private Instrument parent;
	private LoopStart sibling;
	private Crop child = new Crop( this );
	private int offset, length, gain, pitch;

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
			// Get the left/mono channel from the wav file.
			java.io.InputStream inputStream = parent.getInputStream( value.toString() );
			parent.setAudioData( new AudioData( inputStream, 0 ) );
			setCrop( 0, 0 );
			setGain( 64 );
			setPitch( 0 );
		} catch( java.io.IOException e ) {
			throw new IllegalArgumentException( e );
		}
	}
	
	public void end() {
		AudioData audioData = parent.getAudioData();
		if( length > 0 ) {
			audioData = audioData.crop( offset, length );
		}
		if( gain != 64 ) {
			audioData = audioData.scale( gain );
		}
		if( pitch != 0 ) {
			audioData = audioData.resample( audioData.getSamplingRate(), pitch, false );
		}
		parent.setAudioData( audioData );
	}
	
	public void setCrop( int offset, int length ) {
		this.offset = offset;
		this.length = length;
	}

	public void setGain( int gain ) {
		this.gain = gain;
	}
	
	public void setPitch( int pitch ) {
		this.pitch = pitch;
	}
}
