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
		parent.setPingPong( "On".equals( value ) );
	}
	
	public void end() {
	}
}
