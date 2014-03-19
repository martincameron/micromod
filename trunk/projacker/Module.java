
package projacker;

public class Module implements Element {
	private micromod.Module module;
	private Channels child = new Channels( this );

	public String getToken() {
		return "Module";
	}
	
	public Element getParent() {
		return null;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return child;
	}
	
	public void begin( String value ) {
		System.out.println( getToken() + ": " + value );
		module = new micromod.Module();
		module.setSongName( value );
	}
	
	public void end() {
		System.out.println( getToken() + " end." );
	}

	public void setNumChannels( int numChannels ) {
		module.setNumChannels( numChannels );
	}

	public void setSequenceLength( int length ) {
		module.setSequenceLength( length );
	}

	public void setSequenceEntry( int seqIdx, int patIdx ) {
		module.setSequenceEntry( seqIdx, patIdx );
	}
	
	public micromod.Instrument getInstrument( int insIdx ) {
		return module.getInstrument( insIdx );
	}
	
	public micromod.Pattern getPattern( int patIdx ) {
		return module.getPattern( patIdx );
	}
	
	public micromod.Module getModule() {
		return module;
	}
}
