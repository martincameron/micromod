
package ibxm;

public class Instrument {
	public String name = "";
	public int num_samples = 1;
	public int vibrato_type, vibrato_sweep, vibrato_depth, vibrato_rate;
	public int volume_fade_out;
	public Envelope volume_envelope = new Envelope();
	public Envelope panning_envelope = new Envelope();
	public int[] key_to_sample = new int[ 97 ];
	public Sample[] samples = new Sample[] { new Sample() };

	public void toStringBuffer( StringBuffer out ) {
		out.append( "Name: " + name + '\n' );
		if( num_samples > 0 ) {
			out.append( "Num Samples: " + num_samples + '\n' );
			out.append( "Vibrato Type: " + vibrato_type + '\n' );
			out.append( "Vibrato Sweep: " + vibrato_sweep + '\n' );
			out.append( "Vibrato Depth: " + vibrato_depth + '\n' );
			out.append( "Vibrato Rate: " + vibrato_rate + '\n' );
			out.append( "Volume Fade Out: " + volume_fade_out + '\n' );
			out.append( "Volume Envelope:\n" );
			volume_envelope.toStringBuffer( out );
			out.append( "Panning Envelope:\n" );
			panning_envelope.toStringBuffer( out );
			for( int sam_idx = 0; sam_idx < num_samples; sam_idx++ ) {
				out.append( "Sample " + sam_idx + ":\n" );
				samples[ sam_idx ].toStringBuffer( out );
			}
			out.append( "Key To Sample: " );
			for( int key_idx = 1; key_idx < 97; key_idx++ )
				out.append( key_to_sample[ key_idx ] + ", " );
			out.append( '\n' );
		}
	}
}
