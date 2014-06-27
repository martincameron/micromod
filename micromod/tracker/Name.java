package micromod.tracker;

public class Name implements Element {
	private Instrument parent;
	private Volume sibling;

	public Name( Instrument parent ) {
		this.parent = parent;
		sibling = new Volume( parent );
	}
	
	public String getToken() {
		return "Name";
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
		parent.setName( value );
	}
	
	public void end() {
	}
}
