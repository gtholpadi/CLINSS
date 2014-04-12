
package mllab_clinss;
import java.util.Comparator;

public class DoubleArrayIndexComparator implements Comparator<Integer> {
    private final double[] array;
    public DoubleArrayIndexComparator(double[] array) {
        this.array = array;
    }
    public Integer[] createIndexArray() {
        Integer[] indexes = new Integer[array.length];
        for (int i = 0; i < array.length; i++) {
            indexes[i] = i;
        }
        return indexes;
    }
    @Override
    public int compare(Integer index1, Integer index2) {
        double diff = array[index2] - array[index1];
		if (diff < 0) {
			return -1;
		} else if (diff > 0) {
			return 1;
		} else {
			return 0;
		}
    }
}
