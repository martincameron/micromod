package micromod.compiler;

public class Type implements Element {
	private Chorus parent;

	public Type( Chorus parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "Type";
	}
	
	public Element getParent() {
		return parent;
	}
	
	public Element getSibling() {
		return null;
	}
	
	public Element getChild() {
		return null;
	}
	
	public void begin( String value ) {
		if( "Pulse".equals( value ) ) {
			parent.setChorusType( true );
		} else if( "Phase".equals( value ) ) {
			parent.setChorusType( false );
		} else {
			throw new IllegalArgumentException( "Invalid chorus type (Pulse or Phase): " + value );
		}
	}
	
	public void end() {
	}

	public String description() {
		return "\"Phase\" (Chorus effect modulation type, 'Pulse' or 'Phase'.)";
	}
}
