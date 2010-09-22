
package mumart.micromod.xm;

public class Sample {
	public String name = "";
	public int volume, panning, rel_note, fine_tune;
	public int loop_start, loop_length;
	public short[] sample_data = new short[ 1 ];
	
	public void toStringBuffer( StringBuffer out ) {
		out.append( "Name: " + name + '\n' );
		out.append( "Volume: " + volume + '\n' );
		out.append( "Panning: " + panning + '\n' );
		out.append( "Relative Note: " + rel_note + '\n' );
		out.append( "Fine Tune: " + fine_tune + '\n' );
		out.append( "Loop Start: " + loop_start + '\n' );
		out.append( "Loop Length: " + loop_length + '\n' );
		/*
		out.append( "Sample Data: " );
		for( int idx = 0; idx < sample_data.length; idx++ )
			out.append( sample_data[ idx ] + ", " );
		out.append( '\n' );
		*/
	}
}
