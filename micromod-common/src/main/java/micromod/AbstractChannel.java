package micromod;

public abstract class AbstractChannel {
	public abstract void resample( int[] mixBuf, int offset, int count, int sampleRate, ChannelInterpolation interpolation );

	public abstract void updateSampleIdx( int length, int sampleRate );

	public abstract void row( Note note );

	public abstract void tick();
}
