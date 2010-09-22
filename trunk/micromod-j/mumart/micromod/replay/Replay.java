
package mumart.micromod.replay;

/*
	Basic functionality for a tracker replay.
*/
public interface Replay {
	/*
		Get a String containing version information.
	*/
	public String get_version();

	/*
		Return the sampling rate of playback.
	*/
	public int get_sampling_rate();

	/*
		Returns the minimum size of the buffer required by get_audio().
	*/
	public int get_mix_buffer_length();

	/*
		Return the text stored with the song.
		The song name is index 0.
		Instrument names are at index 1 and above.
		If the index is out of range null is returned.
	*/
	public String get_string( int index );

	/*
		Set the playback to begin at the specified pattern position.
	*/
	public void set_sequence_pos( int pos );

	/*
		Returns the song duration in samples at the current sampling rate.
	*/
	public int calculate_song_duration();
	
	/*
		Seek to approximately the specified sample position.
		The actual sample position reached is returned.
	*/
	public int seek( int sample_pos );

	/*
		Generate audio.
		The number of samples placed into output_buf is returned.
		The output buffer length must be at least that returned by get_mix_buffer_length().
		A "sample" is a pair of 16-bit integer amplitudes, one for each of the stereo channels.
	*/
	public int get_audio( int[] output_buf );
}
