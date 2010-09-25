
package mumart.micromod.replay;

import java.io.*;
import javax.sound.sampled.*;

/*
	Module player class.
	Provides module loading, oversampling and simpler buffering.
*/
public class Player {
	private static final int OVERSAMPLE = 2;

	private Replay replay;
	private boolean loop;
	private int sampling_rate, duration, sample_pos;
	private int filt_l, filt_r, mix_pos, mix_len;
	private int[] mix_buffer;

	/* Initialise a Player to play the specified music module. */
	public Player( byte[] module_data, int sampling_rate, boolean loop ) {
		this.sampling_rate = sampling_rate;
		this.loop = loop;
		replay = init_replay( module_data, sampling_rate * OVERSAMPLE );
		duration = replay.calculate_song_duration() / OVERSAMPLE;
		mix_buffer = new int[ replay.get_mix_buffer_length() ];
	}

	/* Get version information from the playback engine.*/
	public String get_version() {
		return replay.get_version();
	}

	/* Get the song and instrument names, or null if idx is out of range. */
	public String get_string( int idx ) {
		return replay.get_string( idx );
	}

	/* Get the song duration in samples at the current sampling rate. */
	public int get_song_duration() {
		return duration;
	}

	/* Set playback to begin at the specified pattern position. */
	public void set_sequence_pos( int pos ) {
		loop = true;
		mix_pos = mix_len = sample_pos = 0;
		replay.set_sequence_pos( pos );
	}

	/*
		Seek to approximately the specified sample position.
		The actual position reached is returned.
	*/
	public int seek( int sample_pos ) {
		mix_pos = mix_len = 0;
		return sample_pos = replay.seek( sample_pos * OVERSAMPLE ) / OVERSAMPLE;
	}

	/*
		Get up to the specified number of stereo samples of audio into
		the specified buffer. Returns false if the song has finished.
	*/
	public boolean get_audio( short[] output, int offset, int count ) {
		int[] mix_buf = mix_buffer;
		while( count > 0 ) {
			if( mix_pos >= mix_len ) {
				// More audio required from Replay.
				mix_pos = 0;
				if( !loop ) {
					sample_pos += mix_len;
					if( sample_pos >= duration ) {
						mix_len = 0;
						// Song finished, zero rest of output buffer.
						int end = offset + count * 2;
						while( offset < end ) output[ offset++ ] = 0;
						return false;
					}
				}
				mix_len = downsample( mix_buf, replay.get_audio( mix_buf ) );
			}
			// Calculate maximum number of samples to copy.
			int len = mix_len - mix_pos;
			if( len > count ) len = count;
			// Clip and copy samples to output.
			int end = offset + len * 2;
			int mix_idx = mix_pos * 2;
			while( offset < end ) {
				int sam = mix_buf[ mix_idx++ ];
				if( sam > 32767 ) sam = 32767;
				if( sam < -32768 ) sam = -32768;
				output[ offset++ ] = ( short ) sam;
			}
			mix_pos += len;
			count -= len;
		}
		return ( sample_pos + mix_pos ) < duration;
	}

	/*
		2:1 downsampling with simple but effective anti-aliasing.
		Count is the number of stereo samples to process, and must be even.
	*/
	private int downsample( int[] buf, int count ) {
		int fl = filt_l, fr = filt_r;
		int in_idx = 0, out_idx = 0;
		while( out_idx < count ) {	
			int out_l = fl + ( buf[ in_idx++ ] >> 1 );
			int out_r = fr + ( buf[ in_idx++ ] >> 1 );
			fl = buf[ in_idx++ ] >> 2;
			fr = buf[ in_idx++ ] >> 2;
			buf[ out_idx++ ] = out_l + fl;
			buf[ out_idx++ ] = out_r + fr;
		}
		filt_l = fl;
		filt_r = fr;
		return count >> 1;
	}

	/*
		Attempt to initialise a replay from the specified module data.
	*/
	public static Replay init_replay( byte[] module_data, int sampling_rate ) {
		try {
			// Try loading as an XM.
			mumart.micromod.xm.Module module = new mumart.micromod.xm.Module( module_data );
			// XMs generally sound better with interpolation, Mods and S3Ms generally without.
			return new mumart.micromod.xm.IBXM( module, sampling_rate, true );
		} catch( IllegalArgumentException e ) {}
		try {
			// Not an XM, try as an S3M.
			mumart.micromod.s3m.Module module = new mumart.micromod.s3m.Module( module_data );
			return new mumart.micromod.s3m.Micros3m( module, sampling_rate, false );
		} catch( IllegalArgumentException e ) {}
		// Must be a MOD ...
		mumart.micromod.mod.Module module = new mumart.micromod.mod.Module( module_data );
		return new mumart.micromod.mod.Micromod( module, sampling_rate, false );
	}
}
