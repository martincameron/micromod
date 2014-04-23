
package projacker;

public class Module implements Element {
	private micromod.Module module;
	private micromod.Macro[] macros = new micromod.Macro[ 100 ];
	private Channels child = new Channels( this );
	private java.io.File resourceDir;

	public Module( java.io.File resourceDir ) {
		this.resourceDir = resourceDir;
	}

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
	
	public void setMacro( int macroIdx, micromod.Macro macro ) {
		macros[ macroIdx ] = macro;
	}
	
	public micromod.Macro getMacro( int macroIdx ) {
		return macros[ macroIdx ];
	}
	
	public micromod.Pattern getPattern( int patIdx ) {
		return module.getPattern( patIdx );
	}
	
	public micromod.Module getModule() {
		return module;
	}
	
	public java.io.InputStream getInputStream( String path ) throws java.io.IOException {
		return new java.io.FileInputStream( new java.io.File( resourceDir, path ) );
	}
}
