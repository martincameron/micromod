package micromod.compiler;

public class PingPong implements Element {
	private Instrument parent;

	public PingPong( Instrument parent ) {
		this.parent = parent;
	}
	
	public String getToken() {
		return "PingPong";
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
		if( "On".equals( value ) ) {
			parent.setPingPong( true );
		} else if( "Off".equals( value ) ) {
			parent.setPingPong( false );
		} else {
			throw new IllegalArgumentException( "Invalid PingPong parameter (On or Off): " + value );
		}
	}
	
	public void end() {
	}
}
